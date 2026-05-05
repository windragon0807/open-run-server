package io.openur.domain.userchallenge.service;

import io.openur.domain.challenge.model.ChallengeStage;
import io.openur.domain.challenge.repository.ChallengeStageRepository;
import io.openur.domain.user.model.User;
import io.openur.domain.user.repository.UserRepository;
import io.openur.domain.userchallenge.model.UserChallenge;
import io.openur.domain.userchallenge.repository.UserChallengeRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lazy-create user_challenge rows on first progress action.
 *
 * 별도 컴포넌트로 분리한 이유:
 * Self-invocation 회피 — 같은 빈 안에서 호출해도 Spring AOP가 트랜잭션을 적용하도록.
 *
 * 동시성 주의: 같은 userId에 대한 두 호출이 동시에 도달하면 양쪽 모두
 * existsByUserId == false를 보고 INSERT를 시도해 row가 중복될 수 있음.
 * 후속 PR에서 tb_users_challenges에 (user_id, challenge_stage_id) UNIQUE 제약을
 * 추가하고 DataIntegrityViolationException을 graceful하게 처리하는 것을 권장.
 *
 * 트랜잭션 propagation: REQUIRED를 사용하여 호출자(예: BungService.completeBung)와
 * 같은 트랜잭션 안에서 동작. bulkInsertUserChallenges 내부의 entityManager.clear()가
 * 호출자의 영속성 컨텍스트를 비울 수 있으므로 호출자는 publishChallengeCheck를
 * 호출한 이후 캐시된 엔티티에 의존하지 않아야 함.
 */
@Component
@RequiredArgsConstructor
public class UserChallengeInitializer {

    private final UserChallengeRepository userChallengeRepository;
    private final ChallengeStageRepository stageRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void ensureInitialized(String userId) {
        if (userChallengeRepository.existsByUserId(userId)) {
            return;
        }

        User user;
        try {
            user = userRepository.findById(userId);
        } catch (NoSuchElementException e) {
            return;
        }

        List<UserChallenge> newUserChallenges = new ArrayList<>();
        Pageable pageable = PageRequest.of(0, 100);
        Page<ChallengeStage> challengeStages;

        do {
            challengeStages = stageRepository.findAllByMinimumStages(pageable);
            challengeStages.forEach(stage ->
                newUserChallenges.add(new UserChallenge(user, stage))
            );
            pageable = pageable.next();
        } while (challengeStages.hasNext());

        if (!newUserChallenges.isEmpty()) {
            userChallengeRepository.bulkInsertUserChallenges(newUserChallenges);
        }
    }
}
