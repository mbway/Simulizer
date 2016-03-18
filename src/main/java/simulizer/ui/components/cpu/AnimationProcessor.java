package simulizer.ui.components.cpu;

import simulizer.ui.components.CPU;
import simulizer.ui.components.cpu.listeners.CPUListener;
import simulizer.utils.ThreadUtils;
import simulizer.utils.UIUtils;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Processes animations and queues them up to be run by an executor service
 */
public class AnimationProcessor {

	/**
	 * Represents an animation to be run
	 */
    class Animation{
        public int delayFromCycleStart;
		public int delayFromInstructionStart;
        public Runnable job;

        public Animation(int delayFromCycleStart, int delayFromInstructionStart, Runnable job){
            this.delayFromCycleStart = delayFromCycleStart;
			this.delayFromInstructionStart = delayFromInstructionStart;
            this.job = job;
        }
    }

    private final ScheduledExecutorService executorService;
    private final ScheduledFuture<?> executorTask;
    private final PriorityBlockingQueue<Animation> animationTasks;
	private long cycleStartTime; // in ms
	private int cycleDelay; // in ms
    public CPUListener cpuListener;
    public CPU cpuVisualisation;
    public boolean showingWarning;

	/**
	 * Sets initial values and sets up the executor service and task
	 * @param cpuVisualisation The cpu visualisation
     */
    public AnimationProcessor(CPU cpuVisualisation){
        this.cpuVisualisation = cpuVisualisation;
		int dispatchInterval = 20;

        cycleStartTime = -1;
        showingWarning = false;

		animationTasks = new PriorityBlockingQueue<>(10, (a1, a2) -> Integer.compare(a1.delayFromCycleStart, a2.delayFromCycleStart));
        executorService = Executors.newSingleThreadScheduledExecutor(
                new ThreadUtils.NamedThreadFactory("CPU-Visualisation-Job-Dispatch"));
        executorTask = executorService.scheduleAtFixedRate(
				this::dispatchAnimationJobs, dispatchInterval, dispatchInterval, TimeUnit.MILLISECONDS);
    }

	/**
	 * Run each time there is a new cycle, resets the cycleDelay and sets the cycleStartTime
	 * also clears the tasks if there are any left
	 */
	public synchronized void newCycle() {
		cycleDelay = 0;
		cycleStartTime = System.currentTimeMillis();
		if(!animationTasks.isEmpty()) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				UIUtils.showExceptionDialog(e);
			}
			animationTasks.clear();
		}
	}

	/**
	 * Processes animations jobs
	 */
	private void dispatchAnimationJobs() {
		if(!animationTasks.isEmpty()) {
			// Are there animation jobs?

			// Is the clock speed too fast? If so show the warning label
			if(cpuListener.getSimCpu().getCycleFreq() > 2) {
				animationTasks.clear();
				if(!showingWarning) {
					cpuVisualisation.showText("Please lower the clock speed to less than 2Hz to see animations", 1000, false);
					showingWarning = true;
				}
				return;
			}

			showingWarning = false;
			synchronized (animationTasks) {
				if (animationTasks.size() > 10) {
					// Too many animations, remove the lower priority ones
					ArrayList<Animation> tmp = new ArrayList<>(10);
					animationTasks.drainTo(tmp, 10);
					animationTasks.clear();
					animationTasks.addAll(tmp);
				}

				// Updates the time into the cycle
				long timeIntoCycle = System.currentTimeMillis() - cycleStartTime;

				// Runs the next task when it should be run (its absolute time is lower or equal to the time into the cycle)
				if (animationTasks.peek().delayFromCycleStart <= timeIntoCycle) {
					animationTasks.poll().job.run();
				}
			}
		}
	}

	/**
	 * Sets the cpu listener
	 * @param cpuListener The cpu listener
     */
    public void setCpuListener(CPUListener cpuListener){
        this.cpuListener = cpuListener;
    }

	/**
	 * Shuts down the executor task and service
	 */
    public void shutdown() {
		executorTask.cancel(true);
        executorService.shutdownNow();
    }

	/**
	 * Schedules animations without adding to the instruction list
	 * @param delay The speed of each animation
	 * @param jobs The animations to run
     */
	public void scheduleRegularAnimations(int delay, Runnable... jobs) {
		// Set thisDelay to 0 for each separate instruction
		int thisDelay = 0;
		for(Runnable r : jobs) {
			animationTasks.add(new Animation(cycleDelay, thisDelay, r));
			// Add to the overall cycleDelay (only reset on each cycle) and thisDelay (reset each instruction)
			cycleDelay += delay;
			thisDelay += delay;
		}
	}

	/**
	 * Schedule several animation tasks with a fixed delay in between, and add to the previous instruction list
	 */
	public void scheduleRegularAnimations(String instructionName, int delay, Runnable... jobs) {
		int thisDelay = 0;
		ArrayList<Animation> animationsForInstruction = new ArrayList<>();
		for(Runnable r : jobs) {
			Animation animation = new Animation(cycleDelay, thisDelay, r);
			animationTasks.add(animation);
			animationsForInstruction.add(animation);
			// Add to the overall cycleDelay (reset on each cycle) and thisDelay (reset each instruction)
			cycleDelay += delay;
			thisDelay += delay;
		}

		// Add to the list of previous instructions
		cpuVisualisation.previousInstructions.addInstruction(instructionName, animationsForInstruction);
	}

	/**
	 * Replays animations
	 * @param animations The animations to replay
     */
	public void replayAnimations(ArrayList<Animation> animations){
		// Start a new cycle to get the timings right
		newCycle();
		for (Animation a : animations){
			// Set the delayFromCycleStart time to the old delayFromInstructionStart time, this needs to be done as the
			// fetch will add some time so for example the delayFromCycleStart could be 5000 meaning there would be a
			// 5 second delay before the animation runs, which is not wanted on a replay.
			a.delayFromCycleStart = a.delayFromInstructionStart;
		}
		animationTasks.addAll(animations);
	}
}