package timenexus.utils;

import java.lang.reflect.InvocationTargetException;

import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskIterator;

/*
 * Basic factory to construct a new task.
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public class RegisterTask<E extends AbstractTask> extends AbstractTaskFactory {

	Class<E> task;
	CytoPanelComponent panel;
	
	public RegisterTask( Class<E> task ) {
		super();
		this.task = task;
	}
	
	public RegisterTask( Class<E> task, CytoPanelComponent panel ) {
		super();
		this.task = task;
		this.panel = panel;
	}

	public TaskIterator createTaskIterator() {		
		try {
			if ( panel != null )
				return new TaskIterator( task.getDeclaredConstructor( CytoPanelComponent.class ).newInstance( panel ) );
			else
				return new TaskIterator( task.getDeclaredConstructor().newInstance() );
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
			return new TaskIterator();
		}
	}
	
	public boolean isReady() { return true; }
}