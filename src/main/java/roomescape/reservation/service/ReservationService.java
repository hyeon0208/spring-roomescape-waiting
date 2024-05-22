package roomescape.reservation.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roomescape.auth.dto.LoginMember;
import roomescape.common.dto.MultipleResponses;
import roomescape.member.domain.Member;
import roomescape.member.repository.MemberRepository;
import roomescape.reservation.domain.Reservation;
import roomescape.reservation.domain.ReservationTime;
import roomescape.reservation.domain.Status;
import roomescape.reservation.domain.Theme;
import roomescape.reservation.domain.Waitings;
import roomescape.reservation.dto.MemberReservationResponse;
import roomescape.reservation.dto.ReservationResponse;
import roomescape.reservation.dto.ReservationSaveRequest;
import roomescape.reservation.dto.ReservationSearchConditionRequest;
import roomescape.reservation.dto.ReservationWaitingResponse;
import roomescape.reservation.repository.ReservationRepository;
import roomescape.reservation.repository.ReservationTimeRepository;
import roomescape.reservation.repository.ThemeRepository;

@Service
@Transactional
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationTimeRepository reservationTimeRepository;
    private final ThemeRepository themeRepository;
    private final MemberRepository memberRepository;

    public ReservationService(
            ReservationRepository reservationRepository,
            ReservationTimeRepository reservationTimeRepository,
            ThemeRepository themeRepository,
            MemberRepository memberRepository
    ) {
        this.reservationRepository = reservationRepository;
        this.reservationTimeRepository = reservationTimeRepository;
        this.themeRepository = themeRepository;
        this.memberRepository = memberRepository;
    }

    public ReservationResponse saveReservationSuccess(
            ReservationSaveRequest reservationSaveRequest,
            LoginMember loginMember
    ) {
        Reservation reservation = createValidatedReservationOfStatus(reservationSaveRequest, loginMember, Status.SUCCESS);
        validateDuplicatedReservationSuccess(reservation);
        Reservation savedReservation = reservationRepository.save(reservation);

        return ReservationResponse.toResponse(savedReservation);
    }

    public ReservationResponse saveReservationWaiting(
            ReservationSaveRequest reservationSaveRequest,
            LoginMember loginMember
    ) {
        Reservation reservation = createValidatedReservationOfStatus(reservationSaveRequest, loginMember, Status.WAIT);
        validateDuplicatedReservationWaiting(reservation, loginMember);
        Reservation savedReservation = reservationRepository.save(reservation);

        return ReservationResponse.toResponse(savedReservation);
    }

    private Reservation createValidatedReservationOfStatus(
            ReservationSaveRequest reservationSaveRequest,
            LoginMember loginMember,
            Status status
    ) {
        ReservationTime reservationTime = reservationTimeRepository.findById(reservationSaveRequest.getTimeId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약 시간입니다."));

        Theme theme = themeRepository.findById(reservationSaveRequest.getThemeId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 테마입니다."));

        Member member = memberRepository.findById(loginMember.id())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        return reservationSaveRequest.toReservation(member, theme, reservationTime, status);
    }

    private void validateDuplicatedReservationSuccess(Reservation reservation) {
        boolean existsReservation = reservationRepository.existsByDateAndReservationTimeStartAtAndStatus(
                reservation.getDate(),
                reservation.getStartAt(),
                reservation.getStatus()
        );

        if (existsReservation) {
            throw new IllegalArgumentException("중복된 예약이 있습니다.");
        }
    }

    private void validateDuplicatedReservationWaiting(Reservation reservation, LoginMember loginMember) {
        List<Status> statuses = reservationRepository.findStatusesByMemberIdAndDateAndReservationTimeStartAt(
                loginMember.id(),
                reservation.getDate(),
                reservation.getStartAt()
        );

        if (statuses.contains(Status.SUCCESS) || statuses.contains(Status.WAIT)) {
            throw new IllegalArgumentException("예약이 완료되었거나, 대기 상태로 등록된 예약입니다.");
        }
    }

    @Transactional(readOnly = true)
    public ReservationResponse findById(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다."));

        return ReservationResponse.toResponse(reservation);
    }

    @Transactional(readOnly = true)
    public MultipleResponses<ReservationResponse> findAllByStatus(String status) {
        List<ReservationResponse> reservationResponses = reservationRepository.findAllByStatus(Status.from(status)).stream()
                .sorted(Comparator.comparing(Reservation::getDate)
                        .thenComparing(Reservation::getStartAt))
                .map(ReservationResponse::toResponse)
                .toList();

        return new MultipleResponses<>(reservationResponses);
    }

    @Transactional(readOnly = true)
    public MultipleResponses<MemberReservationResponse> findMemberReservations(LoginMember loginMember) {
        List<Reservation> waitingReservations = reservationRepository.findAllByStatus(Status.WAIT);

        Waitings waitings = waitingReservations.stream()
                .sorted(Comparator.comparing(Reservation::getDate)
                        .thenComparing(Reservation::getStartAt)
                        .thenComparing(Reservation::getCreatedAt))
                .collect(Collectors.collectingAndThen(Collectors.toList(), Waitings::new));

        List<MemberReservationResponse> memberReservationResponses = reservationRepository.findAllByMemberId(loginMember.id()).stream()
                .map(reservation -> MemberReservationResponse.toResponse(
                        reservation,
                        waitings.findMemberRank(reservation, loginMember.id())
                )).toList();

        return new MultipleResponses<>(memberReservationResponses);
    }

    @Transactional(readOnly = true)
    public MultipleResponses<ReservationWaitingResponse> findWaitingReservations() {
        List<Reservation> waitingReservations = reservationRepository.findAllByStatus(Status.WAIT);

        List<ReservationWaitingResponse> waitingResponses = waitingReservations.stream()
                .filter(Reservation::isAfterToday)
                .sorted(Comparator.comparing(Reservation::getDate)
                        .thenComparing(Reservation::getStartAt)
                        .thenComparing(Reservation::getCreatedAt))
                .map(ReservationWaitingResponse::toResponse)
                .toList();

        return new MultipleResponses<>(waitingResponses);
    }

    @Transactional(readOnly = true)
    public MultipleResponses<ReservationResponse> findAllBySearchCondition(ReservationSearchConditionRequest request) {
        List<ReservationResponse> reservationResponses = reservationRepository.findAllByThemeIdAndMemberIdAndDateBetween(
                        request.themeId(),
                        request.memberId(),
                        request.dateFrom(),
                        request.dateTo()
                ).stream()
                .sorted(Comparator.comparing(Reservation::getDate)
                        .thenComparing(Reservation::getStartAt))
                .map(ReservationResponse::toResponse)
                .toList();

        return new MultipleResponses<>(reservationResponses);
    }

    public void delete(Long id) {
        reservationRepository.deleteById(id);
    }

    public void cancelById(Long id) {
        Reservation canceledReservation = getCanceledReservation(id);
        updateFirstWaitingReservation(canceledReservation);
    }

    private Reservation getCanceledReservation(Long id) {
        Reservation successReservation = reservationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다."));

        if (successReservation.getDate().equals(LocalDate.now())) {
            throw new IllegalArgumentException("당일 예약은 취소할 수 없습니다.");
        }
        successReservation.updateStatus(Status.CANCEL);

        return successReservation;
    }

    private void updateFirstWaitingReservation(Reservation canceledReservation) {
        Waitings waitings = reservationRepository.findAllByStatus(Status.WAIT).stream()
                .sorted(Comparator.comparing(Reservation::getDate)
                        .thenComparing(Reservation::getStartAt)
                        .thenComparing(Reservation::getCreatedAt))
                .collect(Collectors.collectingAndThen(Collectors.toList(), Waitings::new));

        waitings.findFirstWaitingReservationByCanceledReservation(canceledReservation)
                .ifPresent(reservation -> reservation.updateStatus(Status.SUCCESS));
    }
}
