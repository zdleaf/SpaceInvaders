package spaceinvaders.utility;

/** Automatically turns on, at a fixed rate. */
public class AutoSwitch implements Service<Void> {
  private final ServiceState running = new ServiceState();
  private final ServiceState switchState = new ServiceState();
  private final Long rateMs;

  public AutoSwitch(long rateMs) {
    this.rateMs = rateMs;
    running.set(true);
  }

 /**
  * Start with a delay, and then turn on the switch at a fixed rate.
  *
  * @throws InterruptedException - if interrupted prior to shutdown.
  */
  @Override
  public Void call() throws InterruptedException {
    while (running.get()) {
      try {
        Thread.sleep(rateMs);
      } catch (InterruptedException intException) {
        if (running.get()) {
          throw new InterruptedException();
        }
      }
      switchState.set(true);
      try {
        wait();
      } catch (InterruptedException intException) {
        if (running.get()) {
          throw new InterruptedException();
        }
      }
    }
    return null;
  }

  @Override
  public void shutdown() {
    running.set(false);
  }

  public boolean isOn() {
    return switchState.get();
  }

  public void toggle() {
    boolean value = switchState.get();
    switchState.set(!value);
    notify();
  }
}