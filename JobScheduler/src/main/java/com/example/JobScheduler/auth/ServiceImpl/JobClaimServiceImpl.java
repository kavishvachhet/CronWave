package com.example.JobScheduler.auth.ServiceImpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.ExecutableUpdateOperation.FindAndModifyWithOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.example.JobScheduler.auth.Services.JobClaimService;
import com.example.JobScheduler.auth.entity.Job;
import com.example.JobScheduler.auth.entity.jobstatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobClaimServiceImpl implements JobClaimService{

    private final MongoTemplate mongoTemplate;
    
    @Override
    public List<Job> claimDueJobs() {
        List<Job> j1 = new ArrayList<>();
        
        while (true) {
            Query query = new Query(
                Criteria.where("status").is(jobstatus.ACTIVE).and("nextRunAt")
                .lte(LocalDateTime.now())
            );

            Update update = new Update().set("status", jobstatus.RUNNING);

            FindAndModifyOptions options = FindAndModifyOptions
                .options()
                .returnNew(true); 

            Job claimed_job = mongoTemplate.findAndModify(query, update, options,Job.class);
            if(claimed_job==null){
                break;
            }
            j1.add(claimed_job);
        }
        return j1;
    }
    

}
