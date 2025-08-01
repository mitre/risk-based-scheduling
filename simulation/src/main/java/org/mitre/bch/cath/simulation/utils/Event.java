/** Event is a steppable event on the schedule. This is used to track the schedule, since sim.engine.Schedule
 * doesn't have a mechanism to return the heap.
 *
 * @author H. Haven Liu, THe MITRE Corporation
 */

package org.mitre.bch.cath.simulation.utils;

import com.google.common.base.Objects;
import sim.engine.Steppable;

public class Event {
    /** the event to be put on the schedule */
    private final Steppable event;

    /** the scheduled time */
    private final double time;

    /** the class/method/line that scheduled the event */
    private final String scheduledBy;

    /** Constructor
     *
     * @param event the event to be put on the schedule
     * @param time the scheduled time
     * @param scheduledBy the thing that scheduled the event
     */
    public Event(Steppable event, double time, String scheduledBy) {
        this.event = event;
        this.time = time;
        this.scheduledBy = scheduledBy;
    }

    /** Get CSV headers. Correspond to the csvRow() method.
     *
     * @return CSV header
     */
    public static String[] csvHeader() {
        return new String[] {
          "time", "time", "event class", "event instance", "scheduled by"
        };
    }

    /** Get the csv row value to populate a CSVPrinter
     *
     * @return CSV Row.
     */
    public String[] csvRow() {
        return new String[]{
                String.valueOf(time),
                LoggerHelper.toTime(time),
                event.getClass().getName(),
                event.toString(),
                scheduledBy
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Event event1 = (Event) o;
        return Double.compare(event1.time, time) == 0 && event.toString().equals(event1.event.toString()) && Objects.equal(scheduledBy, event1.scheduledBy);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(event, time, scheduledBy);
    }

    @Override
    public String toString() {
        return "Event{" +
                "event=" + event +
                ", time=" + time +
                ", scheduledBy='" + scheduledBy + '\'' +
                '}';
    }
}
