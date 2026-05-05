package io.openur.domain.challenge.service;

import io.openur.domain.challenge.dto.GeneralChallengeDto;
import io.openur.domain.challenge.dto.RepetitiveChallengeTreeDto;
import io.openur.domain.challenge.exception.ChallengeStageInvalid;
import io.openur.domain.challenge.model.ChallengeStage;
import io.openur.domain.challenge.repository.ChallengeStageRepository;
import io.openur.domain.user.model.User;
import io.openur.domain.user.repository.UserRepository;
import io.openur.domain.userchallenge.model.UserChallenge;
import io.openur.domain.userchallenge.repository.UserChallengeRepository;
import io.openur.global.security.UserDetailsImpl;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChallengeService {
    private final UserRepository userRepository;
    private final UserChallengeRepository userChallengeRepository;
    private final ChallengeStageRepository challengeStageRepository;

    public Page<GeneralChallengeDto> getGeneralChallengeList(
        UserDetailsImpl userDetails, Pageable pageable
    ) {
        User user = userRepository.findUser(userDetails.getUser());

        return userChallengeRepository.findUncompletedChallengesByUserId(
            user.getUserId(), pageable
        ).map(row -> new GeneralChallengeDto(row.stage(), row.userChallenge()));
    }

    public Page<GeneralChallengeDto> getCompletedChallengeList(
        UserDetailsImpl userDetails, Pageable pageable
    ) {
        User user = userRepository.findUser(userDetails.getUser());

        return userChallengeRepository.findCompletedChallengesByUserId(
            user.getUserId(), pageable
        ).map(GeneralChallengeDto::new);
    }

    public Page<GeneralChallengeDto> getRepetitiveChallengeList(
        UserDetailsImpl userDetails, Pageable pageable
    ) {
        User user = userRepository.findUser(userDetails.getUser());

        return userChallengeRepository.findRepetitiveChallengesByUserId(
            user.getUserId(), pageable
        ).map(row -> new GeneralChallengeDto(row.stage(), row.userChallenge()));
    }

    public RepetitiveChallengeTreeDto getRepetitiveChallengeDetail(
        UserDetailsImpl userDetails, Long challengeId
    ) {
        User user = userRepository.findUser(userDetails.getUser());

        Map<Long, UserChallenge> userChallengeMap = userChallengeRepository
            .findRepetitiveUserChallengesMappedByStageId(
                user.getUserId(), challengeId
            );

        List<ChallengeStage> challengeStages = challengeStageRepository
            .findAllByChallengeId(challengeId);

        if (challengeStages.isEmpty())
            throw new ChallengeStageInvalid("No challenge stages found");

        return new RepetitiveChallengeTreeDto(userChallengeMap, challengeStages);
    }
}
