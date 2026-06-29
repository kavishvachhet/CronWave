import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootApplication
public class CheckRedis {
    
    @Autowired
    private StringRedisTemplate redisTemplate;

    public static void main(String[] args) {
        SpringApplication.run(CheckRedis.class, args);
    }

    @org.springframework.context.annotation.Bean
    public CommandLineRunner run() {
        return args -> {
            Set<String> keys = redisTemplate.keys("*");
            System.out.println("REDIS KEYS: " + keys);
            System.exit(0);
        };
    }
}
