package scheduledtasks;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ScheduledTask {
    private ScheduledExecutorService executor;
    private List<Method> scheduledTasks = new ArrayList<>();

    public ScheduledTask() {

        Method[] methods = this.getClass().getMethods();
        for (Method method : methods) {
            Scheduled annotation = method.getAnnotation(Scheduled.class);
            if (annotation != null)
                scheduledTasks.add(method);
        } 


        if (!scheduledTasks.isEmpty()) {
            executor = new ScheduledThreadPoolExecutor(scheduledTasks.size());

            for (Method method : scheduledTasks) {
                Scheduled annotation = method.getAnnotation(Scheduled.class);
                executor.scheduleWithFixedDelay(
                    () -> {
                        try {
                            method.invoke(this, new Object[0]);
                        } catch (Exception e) {
                            System.out.println("Scheduled task failed to start");
                        } 
                    }, 
                    annotation.initialDelay(), 
                    annotation.delayMillis(),
                    TimeUnit.MILLISECONDS
                );
            }

        }
        
    }


    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
    }


    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Scheduled {
        int delayMillis() default 1000;
        int initialDelay() default 0;
    }


    

    // helper classes
    public static class ScheduledTaskException extends RuntimeException {
        public ScheduledTaskException(String message, Exception cause) {
            super(message, cause);
        }
    }

     

}
