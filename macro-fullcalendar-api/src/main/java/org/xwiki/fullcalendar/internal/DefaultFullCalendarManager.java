/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.fullcalendar.internal;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.fullcalendar.FullCalendarManager;
import org.xwiki.velocity.tools.JSONTool;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.util.CompatibilityHints;

/**
 * Default implementation for {@link FullCalendarManager}.
 * 
 * @version $Id$
 * @since 2.1
 */
@Component
@Singleton
public class DefaultFullCalendarManager implements FullCalendarManager
{

    @Override
    public String iCalToJSON(String iCalStringURL) throws Exception
    {
        URL iCalURL = new URL(iCalStringURL);

        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_PARSING, true);
        CalendarBuilder builder = new CalendarBuilder();
        URLConnection conn = iCalURL.openConnection();
        InputStream in = conn.getInputStream();
        Calendar calendar = builder.build(in);

        TimeZoneRegistry timeZoneRegistry = builder.getRegistry();
        // Some calendars rely on X-WR-TIMEZONE property from the main component for defining the timeZone.
        String timeZoneValue = getCalendarValue(calendar, "X-WR-TIMEZONE");
        if (timeZoneValue == null) {
            // Some calendars rely on TZID property from the VTIMEZONE component for defining the timeZone.
            timeZoneValue = getComponentValue(calendar.getComponent("VTIMEZONE"), "TZID");
        }
        TimeZone timeZone = timeZoneRegistry.getTimeZone(timeZoneValue);

        ArrayList<Object> jsonArrayList = new ArrayList<Object>();

        // FullCalendar will accept ISO8601 date strings written with hours, minutes, seconds, and milliseconds.
        DateFormat jsonDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");

        for (CalendarComponent component : calendar.getComponents()) {
            if (component.getName().equals("VEVENT")) {
                Map<String, Object> jsonMap = new HashMap<String, Object>();
                jsonMap.put("id", getComponentValue(component, "UID"));
                jsonMap.put("title", getComponentValue(component, "SUMMARY"));

                String startDateValue = getComponentValue(component, "DTSTART");
                String endDateValue = getComponentValue(component, "DTEND");

                // If either the start or end value has a "T" as part of the ISO8601 date string, allDay will become
                // false. Otherwise, it will be true.
                boolean allDay = startDateValue.contains("T") || endDateValue.contains("T");
                jsonMap.put("allDay", !allDay);

                DateTime startDateTime = new DateTime(startDateValue, timeZone);
                jsonMap.put("start", jsonDateFormat.format(startDateTime));

                DateTime endDateTime = new DateTime(endDateValue, timeZone);
                jsonMap.put("end", jsonDateFormat.format(endDateTime));

                // Non-standard fields in each Event Object. FullCalendar will not modify or delete these fields.
                jsonMap.put("description", getComponentValue(component, "DESCRIPTION"));
                jsonMap.put("location", getComponentValue(component, "LOCATION"));
                jsonMap.put("status", getComponentValue(component, "STATUS"));

                jsonArrayList.add(jsonMap);
            }
        }

        return new JSONTool().serialize(jsonArrayList);
    }

    private String getComponentValue(CalendarComponent component, String propertyName)
    {
        return component.getProperty(propertyName) == null ? null : component.getProperty(propertyName).getValue();
    }

    private String getCalendarValue(Calendar calendar, String propertyName)
    {
        return calendar.getProperty(propertyName) == null ? null : calendar.getProperty(propertyName).getValue();
    }
}
