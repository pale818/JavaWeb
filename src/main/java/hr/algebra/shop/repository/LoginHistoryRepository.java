package hr.algebra.shop.repository;
import hr.algebra.shop.model.LoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {
}
