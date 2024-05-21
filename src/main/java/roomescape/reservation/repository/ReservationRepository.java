package roomescape.reservation.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import roomescape.reservation.domain.Reservation;
import roomescape.reservation.domain.Status;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Override
    @EntityGraph(attributePaths = {"member", "theme", "reservationTime"})
    List<Reservation> findAll();

    @Query("""
            select r.reservationTime.id from Reservation r 
            join fetch ReservationTime rt on r.reservationTime.id = rt.id  
            where r.date = :date and r.theme.id = :themeId
            """)
    List<Long> findTimeIdsByDateAndThemeId(LocalDate date, Long themeId);

    @EntityGraph(attributePaths = {"member", "theme", "reservationTime"})
    List<Reservation> findAllByMemberId(Long memberId);

    @EntityGraph(attributePaths = {"member", "theme", "reservationTime"})
    List<Reservation> findAllByThemeIdAndMemberIdAndDateBetween(
            Long themeId,
            Long memberId,
            LocalDate dateFrom,
            LocalDate dateTo
    );

    List<Reservation> findAllByStatus(Status status);

    boolean existsByDateAndReservationTimeStartAtAndStatus(LocalDate date, LocalTime startAt, Status status);

    boolean existsByMemberIdAndDateAndReservationTimeStartAtAndStatus(Long memberId, LocalDate date, LocalTime startAt, Status status);
}
