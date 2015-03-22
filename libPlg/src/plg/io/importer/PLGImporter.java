package plg.io.importer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import plg.annotations.Importer;
import plg.exceptions.IllegalSequenceException;
import plg.exceptions.UnsupportedPLGFileFormat;
import plg.generator.IProgressVisualizer;
import plg.generator.scriptexecuter.IntegerScriptExecutor;
import plg.generator.scriptexecuter.StringScriptExecutor;
import plg.io.exporter.PLGExporter;
import plg.model.FlowObject;
import plg.model.Process;
import plg.model.activity.Task;
import plg.model.data.DataObject;
import plg.model.data.IDataObjectOwner;
import plg.model.data.IntegerDataObject;
import plg.model.data.StringDataObject;
import plg.model.event.EndEvent;
import plg.model.event.StartEvent;
import plg.model.gateway.Gateway;
import plg.model.sequence.Sequence;
import plg.utils.Logger;
import plg.utils.ZipHelper;

/**
 * This class imports a PLG file generated by the PLG exporter
 * 
 * @author Andrea Burattin
 * @see PLGExporter
 */
@Importer(
	name = "PLG file",
	fileExtension = "plg"
)
public class PLGImporter extends FileImporter {

	@Override
	public Process importModel(String filename, IProgressVisualizer progress) {
		progress.setIndeterminate(true);
		progress.setText("Importing PLG file...");
		progress.start();
		Logger.instance().info("Starting process import");
		try {
			if (ZipHelper.isValid(new File(filename))) {
				Process p = importFromPlg1(filename);
				progress.finished();
				return p;
			} else {
				Process p = importFromPlg2(filename);
				progress.finished();
				return p;
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		Logger.instance().info("Process import complete");
		progress.finished();
		return null;
	}
	
	/**
	 * This method imports a PLG v.1 file. Still uncomplete.
	 * 
	 * @param filename
	 * @return
	 * @throws UnsupportedPLGFileFormat
	 */
	protected Process importFromPlg1(String filename) throws UnsupportedPLGFileFormat {
		throw new UnsupportedPLGFileFormat("This implementation currently support only second generation of PLG files");
	}
	
	/**
	 * This method imports a PLG v.2 file
	 * 
	 * @param filename
	 * @return
	 * @throws JDOMException
	 * @throws IOException
	 * @throws UnsupportedPLGFileFormat
	 */
	protected Process importFromPlg2(String filename) throws JDOMException, IOException, UnsupportedPLGFileFormat {
		FileInputStream input = new FileInputStream(filename);
		SAXBuilder builder = new SAXBuilder();
		Document document = (Document) builder.build(input);
		
		Element process = document.getRootElement();
		Element meta = process.getChild("meta");
		Element elements = process.getChild("elements");
		
		Element LibPLG_NAME = meta.getChild("LibPLG_NAME");
		Element libPLG_VERSION = meta.getChild("libPLG_VERSION");
		
		// check the current file PLG version
		if (LibPLG_NAME == null || libPLG_VERSION == null) {
			throw new UnsupportedPLGFileFormat("The PLG file provided is not supported");
		}
		
		// creates the new process
		Process p = new Process(meta.getChildText("name"));
		p.setId(meta.getChildText("id"));

		// data objects
		for (Element ss : elements.getChildren("dataObject")) {
			String type = ss.getAttributeValue("type");
			DataObject d = null;
			if (type.equals("DataObject")) {
				d = new DataObject(p);
				d.setValue(ss.getAttributeValue("value"));
			} else if (type.equals("StringDataObject")) {
				String script = ss.getChildText("script");
				StringScriptExecutor executor = new StringScriptExecutor(script);
				d = new StringDataObject(p, executor);
			} else if (type.equals("IntegerDataObject")) {
				String script = ss.getChildText("script");
				IntegerScriptExecutor executor = new IntegerScriptExecutor(script);
				d = new IntegerDataObject(p, executor);
			}
			d.setName(ss.getAttributeValue("name"));
			d.setComponentId(ss.getAttribute("id").getIntValue());
		}
		// start events
		for (Element ss : elements.getChildren("startEvent")) {
			StartEvent s = p.newStartEvent();
			s.setComponentId(ss.getAttribute("id").getIntValue());
			for (Element dos : ss.getChildren("dataObject")) {
				s.addDataObject((DataObject) p.searchComponent(dos.getAttributeValue("id")));
			}
		}
		// end events
		for (Element ss : elements.getChildren("endEvent")) {
			EndEvent e = p.newEndEvent();
			e.setComponentId(ss.getAttribute("id").getIntValue());
			for (Element dos : ss.getChildren("dataObject")) {
				e.addDataObject((DataObject) p.searchComponent(dos.getAttributeValue("id")));
			}
		}
		// tasks
		for (Element ss : elements.getChildren("task")) {
			Task t = p.newTask(ss.getAttributeValue("name"));
			t.setComponentId(ss.getAttribute("id").getIntValue());
			String script = ss.getChildText("script");
			IntegerScriptExecutor executor = new IntegerScriptExecutor(script);
			t.setActivityScript(executor);
			for (Element dos : ss.getChildren("dataObject")) {
				t.addDataObject((DataObject) p.searchComponent(dos.getAttributeValue("id")));
			}
		}
		// gateways
		for (Element ss : elements.getChildren("gateway")) {
			Gateway g = null;
			if (ss.getAttributeValue("type").equals("ExclusiveGateway")) {
				g = p.newExclusiveGateway();
			} else if (ss.getAttributeValue("type").equals("ParallelGateway")) {
				g = p.newParallelGateway();
			}
			g.setComponentId(ss.getAttribute("id").getIntValue());
			for (Element dos : ss.getChildren("dataObject")) {
				g.addDataObject((DataObject) p.searchComponent(dos.getAttributeValue("id")));
			}
		}
		// sequences
		for (Element ss : elements.getChildren("sequenceFlow")) {
			try {
				Sequence s = p.newSequence(
						(FlowObject) p.searchComponent(ss.getAttributeValue("sourceRef")),
						(FlowObject) p.searchComponent(ss.getAttributeValue("targetRef")));
				s.setComponentId(ss.getAttribute("id").getIntValue());
				for (Element dos : ss.getChildren("dataObject")) {
					s.addDataObject((DataObject) p.searchComponent(dos.getAttributeValue("id")));
				}
			} catch (IllegalSequenceException e) {
				e.printStackTrace();
			}
		}
		// data objects owner
		for (Element ss : elements.getChildren("dataObject")) {
			DataObject d = (DataObject) p.searchComponent(ss.getAttributeValue("id"));
			String owner = ss.getAttributeValue("owner");
			if (owner != null) {
				d.setObjectOwner((IDataObjectOwner) p.searchComponent(owner));
			}
		}
	
		return p;
	}
}
