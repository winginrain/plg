package plg.gui;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import plg.gui.controller.ApplicationController;
import plg.utils.CPUUtils;
import plg.utils.Logger;

/**
 * Main application class
 * 
 * @author Andrea Burattin
 */
public class GUI {

	public static void main(String args[]) throws ClassNotFoundException, InstantiationException, IllegalAccessException, UnsupportedLookAndFeelException {
		
		UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		
		ApplicationController.instance().getMainFrame().setVisible(true);
		Logger.instance().debug("Application started!");
		Logger.instance().debug("You have " + CPUUtils.CPUAvailable() + " CPU(s) available");
	}
}
