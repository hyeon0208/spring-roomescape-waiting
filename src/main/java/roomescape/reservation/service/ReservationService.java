package roomescape.reservation.service;

import java.util.List;
import org.springframework.stereotype.Service;
import roomescape.member.domain.Member;
import roomescape.member.dto.LoginMember;
import roomescape.member.repository.MemberRepository;
import roomescape.reservation.domain.Reservation;
import roomescape.reservation.domain.ReservationTime;
import roomescape.reservation.domain.Status;
import roomescape.reservation.domain.Theme;
import roomescape.reservation.dto.MemberReservationResponse;
import roomescape.reservation.dto.ReservationResponse;
import roomescape.reservation.dto.ReservationSaveRequest;
import roomescape.reservation.dto.ReservationSearchCondRequest;
import roomescape.reservation.repository.ReservationRepository;
import roomescape.reservation.repository.ReservationTimeRepository;
import roomescape.reservation.repository.ThemeRepository;

@Service
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

    public ReservationResponse save(ReservationSaveRequest reservationSaveRequest, LoginMember loginMember) {
        Reservation reservation = getValidatedReservation(reservationSaveRequest, loginMember);
        validateDuplicateReservation(reservation);
        Reservation savedReservation = reservationRepository.save(reservation);

        return ReservationResponse.toResponse(savedReservation);
    }

    private Reservation getValidatedReservation(ReservationSaveRequest reservationSaveRequest,
                                                LoginMember loginMember) {
        ReservationTime reservationTime = reservationTimeRepository.findById(reservationSaveRequest.getTimeId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약 시간입니다."));

        Theme theme = themeRepository.findById(reservationSaveRequest.getThemeId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 테마입니다."));

        Member member = getValidatedMemberByRole(reservationSaveRequest, loginMember);

        return reservationSaveRequest.toReservation(member, theme, reservationTime, Status.SUCCESS);
    }

    private Member getValidatedMemberByRole(ReservationSaveRequest reservationSaveRequest, LoginMember loginMember) {
        if (loginMember.role().isAdmin()) {
            return memberRepository.findById(reservationSaveRequest.getMemberId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        }
        return memberRepository.findById(loginMember.id())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
    }

    private void validateDuplicateReservation(Reservation reservation) {
        if (reservationRepository.existsByDateAndReservationTime_StartAt(reservation.getDate(),
                reservation.getStartAt())) {
            throw new IllegalArgumentException("중복된 예약이 있습니다.");
        }
    }

    public ReservationResponse findById(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다."));

        return ReservationResponse.toResponse(reservation);
    }

    public List<ReservationResponse> findAll() {
        return reservationRepository.findAll().stream()
                .map(ReservationResponse::toResponse)
                .toList();
    }

    public List<MemberReservationResponse> findMemberReservations(LoginMember loginMember) {
        return reservationRepository.findAllByMember_Id(loginMember.id())
                .stream()
                .map(MemberReservationResponse::toResponse)
                .toList();
    }

    public List<ReservationResponse> findAllBySearchCond(ReservationSearchCondRequest request) {
        return reservationRepository.findAllByTheme_IdAndMember_IdAndDateBetween(
                        request.themeId(),
                        request.memberId(),
                        request.dateFrom(),
                        request.dateTo()
                ).stream()
                .map(ReservationResponse::toResponse)
                .toList();
    }

    public void delete(Long id) {
        reservationRepository.deleteById(id);
    }
}