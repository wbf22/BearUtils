package _test;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import scheduledtasks.ScheduledTask;
import scheduledtasks.ScheduledTask.Scheduled;

public class PrintTask extends ScheduledTask {

    @Scheduled(initialDelay = 100, delayMillis = 1000) 
    public void print() {
        System.out.println("Task run " + ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME));
    }
    
}
