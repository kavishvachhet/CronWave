package com.example.JobScheduler.auth.ServiceImpl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutionException;

import org.apache.coyote.BadRequestException;
import org.springframework.stereotype.Service;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.example.JobScheduler.auth.Services.CronSerivce;

@Service
public class CronServiceImpl implements CronSerivce{

    private final CronParser parser;

    public CronServiceImpl(){
        this.parser = new CronParser(
            CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ));
    }

    @Override
    public LocalDateTime getNextExecution(String cronexp, LocalDateTime fromTime) throws IllegalArgumentException {
            Cron cron = parser.parse(cronexp);
            try {
                cron.validate();    
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid Cron Expression");
            }
            

            ExecutionTime executionTime = ExecutionTime.forCron(cron);

            ZonedDateTime instant = fromTime.atZone(ZoneId.systemDefault());

            ZonedDateTime nextInstant = executionTime
                .nextExecution(instant)
                .orElseThrow(() ->
                        new RuntimeException("Cannot calculate next execution time"));
            
            return nextInstant.toLocalDateTime();
    
    }
    
}
