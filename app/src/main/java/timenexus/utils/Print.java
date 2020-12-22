package timenexus.utils;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

/*
 * Easier methods to print into Cytoscape or the standard output.
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public final class Print {

	private Print() { throw new RuntimeException(); }
	
	/*
	 * Show a message dialog within Cytoscape but without blocking the main thread.
	 * @param title of the message dialog
	 * @param message of the dialog
	 * @param message type of JOptionPane (.ERROR_MESSAGE, .WARNING_MESSAGE...)  
	 */
	public static void messageDialog( String title, String message, int messageType ) {
		Thread t = new Thread(new Runnable(){
			public void run(){
				JOptionPane.showMessageDialog( new JFrame(), message, title, messageType  );
	        }
	    });
		t.start();
	}
	
	/*
	 * Print anything into the Cytoscape console (standard output).
	 * @param object to print
	 */
	public static void out(Object msg) {
		System.out.println(msg);
	}
	
	/*
	 * Throw an error into the Cytoscape console (standard output).
	 */
	public static void error(Throwable e) {
		out("---");
		out( e.getClass().getName() + ": " + e.getMessage() );
		for (StackTraceElement elmnt : e.getStackTrace() ) out( "\t" + elmnt );
		if ( e.getCause() != null ) error( e.getCause( ));
	}
}
