import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.example.JobScheduler.auth.entity.Job;
import com.example.JobScheduler.auth.entity.jobstatus;

public class TestSerialization {
    public static void main(String[] args) {
        try {
            Job job = Job.builder()
                .id("1")
                .userId("u1")
                .name("test")
                .cronExpression("0 0 * * *")
                .status(jobstatus.ACTIVE)
                .nextRunAt(LocalDateTime.now())
                .build();
            
            PageImpl<Job> page = new PageImpl<>(List.of(job), PageRequest.of(0, 20), 1);
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(page);
            oos.close();
            
            System.out.println("Serialization successful, size: " + baos.toByteArray().length);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
