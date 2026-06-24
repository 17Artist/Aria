/*
 * Copyright 2026 17Artist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package priv.seventeen.artist.aria.callable.builtin;

import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NumberValue;
import priv.seventeen.artist.aria.value.StringValue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class DateTimeFunctions {

    public static void register(CallableManager manager) {
        manager.registerStaticFunction("datetime", "now", d -> new NumberValue(System.currentTimeMillis()));
        manager.registerStaticFunction("datetime", "timestamp", d -> new NumberValue(System.currentTimeMillis() / 1000.0));

        manager.registerStaticFunction("datetime", "format", DateTimeFunctions::format);
        manager.registerStaticFunction("datetime", "parse", DateTimeFunctions::parse);
        manager.registerStaticFunction("datetime", "diff", DateTimeFunctions::diff);

        manager.registerStaticFunction("datetime", "year", d -> fieldOf(d, "year"));
        manager.registerStaticFunction("datetime", "month", d -> fieldOf(d, "month"));
        manager.registerStaticFunction("datetime", "day", d -> fieldOf(d, "day"));
        manager.registerStaticFunction("datetime", "hour", d -> fieldOf(d, "hour"));
        manager.registerStaticFunction("datetime", "minute", d -> fieldOf(d, "minute"));
        manager.registerStaticFunction("datetime", "second", d -> fieldOf(d, "second"));
        manager.registerStaticFunction("datetime", "dayOfWeek", d -> fieldOf(d, "dayOfWeek"));

        manager.registerStaticFunction("datetime", "addDays", (d) -> addTime(d, ChronoUnit.DAYS));
        manager.registerStaticFunction("datetime", "addHours", (d) -> addTime(d, ChronoUnit.HOURS));
        manager.registerStaticFunction("datetime", "addMinutes", (d) -> addTime(d, ChronoUnit.MINUTES));
        manager.registerStaticFunction("datetime", "addSeconds", (d) -> addTime(d, ChronoUnit.SECONDS));
    }

    public static IValue<?> format(InvocationData data) throws AriaException {
        long millis = (long) data.get(0).numberValue();
        String pattern = data.argCount() > 1 ? data.get(1).stringValue() : "yyyy-MM-dd HH:mm:ss";
        try {
            LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
            return new StringValue(dt.format(DateTimeFormatter.ofPattern(pattern)));
        } catch (Exception e) {
            throw new AriaRuntimeException("Date format error: " + e.getMessage());
        }
    }

    public static IValue<?> parse(InvocationData data) throws AriaException {
        String str = data.get(0).stringValue();
        String pattern = data.argCount() > 1 ? data.get(1).stringValue() : "yyyy-MM-dd HH:mm:ss";
        try {
            LocalDateTime dt = LocalDateTime.parse(str, DateTimeFormatter.ofPattern(pattern));
            return new NumberValue(dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        } catch (Exception e) {
            // 尝试只解析日期
            try {
                LocalDate d = LocalDate.parse(str, DateTimeFormatter.ofPattern(pattern));
                return new NumberValue(d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
            } catch (Exception e2) {
                throw new AriaRuntimeException("Date parse error: " + e.getMessage());
            }
        }
    }

    public static IValue<?> diff(InvocationData data) throws AriaException {
        long millis1 = (long) data.get(0).numberValue();
        long millis2 = (long) data.get(1).numberValue();
        String unit = data.argCount() > 2 ? data.get(2).stringValue() : "millis";
        long diffMs = millis2 - millis1;
        double result = switch (unit) {
            case "millis" -> diffMs;
            case "seconds" -> diffMs / 1000.0;
            case "minutes" -> diffMs / 60000.0;
            case "hours" -> diffMs / 3600000.0;
            case "days" -> diffMs / 86400000.0;
            default -> diffMs;
        };
        return new NumberValue(result);
    }

    private static IValue<?> fieldOf(InvocationData data, String field) {
        long millis = (long) data.get(0).numberValue();
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        return new NumberValue(switch (field) {
            case "year" -> dt.getYear();
            case "month" -> dt.getMonthValue();
            case "day" -> dt.getDayOfMonth();
            case "hour" -> dt.getHour();
            case "minute" -> dt.getMinute();
            case "second" -> dt.getSecond();
            case "dayOfWeek" -> dt.getDayOfWeek().getValue();
            default -> 0;
        });
    }

    private static IValue<?> addTime(InvocationData data, ChronoUnit unit) {
        long millis = (long) data.get(0).numberValue();
        long amount = (long) data.get(1).numberValue();
        Instant result = Instant.ofEpochMilli(millis).plus(amount, unit);
        return new NumberValue(result.toEpochMilli());
    }
}
