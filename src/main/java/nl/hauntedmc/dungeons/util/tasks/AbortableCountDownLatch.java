package nl.hauntedmc.dungeons.util.tasks;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

public class AbortableCountDownLatch extends CountDownLatch {
   protected boolean aborted = false;

   public AbortableCountDownLatch(int count) {
      super(count);
   }

   public void abort() {
      if (this.getCount() != 0L) {
         this.aborted = true;

         while (this.getCount() > 0L) {
            this.countDown();
         }
      }
   }

   @Override
   public boolean await(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
      boolean rtrn = super.await(timeout, unit);
      if (this.aborted) {
         throw new AbortableCountDownLatch.AbortedException();
      } else {
         return rtrn;
      }
   }

   @Override
   public void await() throws InterruptedException {
      super.await();
      if (this.aborted) {
         throw new AbortableCountDownLatch.AbortedException();
      }
   }

   @Override
   public void countDown() {
      super.countDown();
   }


   public static class AbortedException extends InterruptedException {
      public AbortedException() {
      }

   }
}
