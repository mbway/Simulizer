package simulizer.ui.components;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Observable;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.layout.Pane;
import simulizer.settings.Settings;
import simulizer.ui.WindowManager;
import simulizer.ui.interfaces.InternalWindow;
import simulizer.ui.interfaces.WindowEnum;
import simulizer.ui.layout.Layout;
import simulizer.ui.layout.WindowLocation;
import simulizer.ui.theme.Theme;
import simulizer.ui.theme.Themeable;

public class Workspace extends Observable implements Themeable {
	private Set<InternalWindow> openWindows = new HashSet<InternalWindow>();
	private final Pane pane = new Pane();
	private WindowManager wm = null;

	/**
	 * A workspace holds all the Internal Windows
	 * 
	 * @param wm
	 *            The stage to listen for resize events
	 */
	public Workspace(WindowManager wm) {
		this.wm = wm;
		pane.getStyleClass().add("background");
		if ((boolean) wm.getSettings().get("workspace.scale-ui.enabled")) {
			ChangeListener<Object> resizeEvent = new ChangeListener<Object>() {
				// Thanks to: http://stackoverflow.com/questions/10773000/how-to-listen-for-resize-events-in-javafx#answer-25812859
				final Timer timer = new Timer("Window-Resizing", true);
				TimerTask task = null;
				int delay = (int) wm.getSettings().get("workspace.scale-ui.delay");

				@Override
				public void changed(ObservableValue<? extends Object> observable, Object oldValue, Object newValue) {
					timer.purge();
					task = new TimerTask() {
						@Override
						public void run() {
							resizeInternalWindows();
						}
					};
					timer.schedule(task, delay);
				}
			};
			
			// Register event listeners
			wm.getPrimaryStage().widthProperty().addListener(resizeEvent);
			wm.getPrimaryStage().heightProperty().addListener(resizeEvent);
			wm.getPrimaryStage().maximizedProperty().addListener(resizeEvent);
		}
	}

	/**
	 * Notifies all open Internal Windows to recalculate their size and positioning
	 */
	public void resizeInternalWindows() {
		double width = pane.getWidth(), height = pane.getHeight();
		if (width > 0 && height > 0)
			for (InternalWindow window : openWindows)
				window.setWorkspaceSize(width, height);
	}

	/**
	 * @return the content pane
	 */
	public Pane getPane() {
		return pane;
	}

	/**
	 * @return the workspace width
	 */
	public double getWidth() {
		return pane.getWidth();
	}

	/**
	 * @return the workspace height
	 */
	public double getHeight() {
		return pane.getHeight();
	}

	/**
	 * Closes all open Internal Windows
	 */
	public void closeAll() {
		for (InternalWindow window : openWindows)
			if (window.isVisible())
				window.close();
		openWindows.clear();
	}

	/**
	 * Finds an Internal Window if it is already open. Returns null if window is not open
	 * 
	 * @param window
	 *            The Internal Window to find
	 * @return The internal window if already open
	 */
	public InternalWindow findInternalWindow(WindowEnum window) {
		for (InternalWindow w : openWindows)
			if (window.equals(w))
				return w;
		return null;
	}

	/**
	 * Opens an Internal Window if it is not already open. Returns the open Internal Window if it is already open
	 * 
	 * @param window
	 *            The Internal Window to find
	 * @return The internal window
	 */
	public InternalWindow openInternalWindow(WindowEnum window) {
		InternalWindow w = findInternalWindow(window);
		if (w != null)
			return w;

		// Not found -> Create a new one
		w = window.createNewWindow();
		assert w != null;
		w.setWindowManager(wm);
		wm.getLayouts().setWindowDimentions(w);
		addWindows(w);
		return w;
	}

	/**
	 * Adds Internal Windows to the workspace (use openInternalWindow instead)
	 * 
	 * @param windows
	 *            List of windows to add to the workspace
	 */
	public void addWindows(InternalWindow... windows) {
		for (InternalWindow window : windows) {
			if (!openWindows.contains(window)) {
				window.setOnCloseAction((e) -> removeWindows(window));
				openWindows.add(window);
				window.setTheme(wm.getThemes().getTheme());
				pane.getChildren().addAll(window);
				window.setGridBounds(wm.getGridBounds());
				window.ready();
			} else {
				System.err.println("Tried to add a window which already exists: " + window.getTitle());
			}
		}
	}

	/**
	 * Removes Internal Windows from the workspace
	 * 
	 * @param windows
	 *            List of Internal Windows to close
	 */
	private void removeWindows(InternalWindow... windows) {
		for (InternalWindow window : windows) {
			if (window.isVisible())
				window.close();
			openWindows.remove(window);
		}
	}

	@Override
	public void setTheme(Theme theme) {
		pane.getStylesheets().clear();
		pane.getStylesheets().add(theme.getStyleSheet("background.css"));
		for (InternalWindow window : openWindows)
			window.setTheme(theme);
	}

	/**
	 * Closes all open Internal Windows except theseWindows
	 * 
	 * @param theseWindows
	 *            The Internal Windows to keep open
	 */
	public void closeAllExcept(InternalWindow[] theseWindows) {
		List<InternalWindow> keepOpen = new ArrayList<InternalWindow>();
		for (int i = 0; i < theseWindows.length; i++)
			keepOpen.add(theseWindows[i]);

		List<InternalWindow> close = new ArrayList<InternalWindow>();
		for (InternalWindow window : openWindows)
			if (!keepOpen.contains(window))
				close.add(window);

		for (InternalWindow window : close)
			removeWindows(window);
	}

	/**
	 * Will generate a Layout of the current workspace
	 * 
	 * @param name
	 *            The name of the layout
	 * @return The layout of the current workspace
	 */
	public Layout generateLayout(String name) {
		int i = 0;
		WindowLocation[] wls = new WindowLocation[openWindows.size()];
		for (InternalWindow window : openWindows) {
			wls[i] = new WindowLocation(WindowEnum.toEnum(window), window.getLayoutX(), window.getLayoutY(), window.getWidth(), window.getHeight());
			i++;
		}

		return new Layout(name, pane.getWidth(), pane.getHeight(), wls);
	}

	public ReadOnlyDoubleProperty widthProperty() {
		return pane.widthProperty();
	}

	public ReadOnlyDoubleProperty heightProperty() {
		return pane.widthProperty();
	}

	/**
	 * @return the settings object
	 */
	public Settings getSettings() {
		return wm.getSettings();
	}

}
