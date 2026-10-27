package wonit.hackathon.a1;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;

// 접속 정보가 명시된 환경에서 실제 DB 스키마와 애플리케이션 시작을 검증한다.
@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "WONMATE_DB_URL", matches = ".+")
class A1ApplicationTests {

	@Test
	void contextLoads() {
	}

}
