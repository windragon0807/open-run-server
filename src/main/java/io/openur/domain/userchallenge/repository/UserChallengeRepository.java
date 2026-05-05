package io.openur.domain.userchallenge.repository;

import io.openur.domain.userchallenge.dto.ChallengeRow;
import io.openur.domain.userchallenge.model.UserChallenge;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

public interface UserChallengeRepository {

    UserChallenge save(UserChallenge userChallenge);

    @Transactional
    void bulkInsertUserChallenges(List<UserChallenge> userChallenges);

    /**
     * Increments the currentCount for challenges of type COUNT
     * @param userChallengeIds List of user challenge IDs to update
     */
    void bulkIncrementCount(List<Long> userChallengeIds);

    /**
     * Updates completion status after NFT airdrop
     * @param completedUserChallengeIds List of challenges that have been completed and had NFT airdropped
     */
    void bulkUpdateCompletedChallenges(List<Long> completedUserChallengeIds);

    void markNftCompleted(Long userChallengeId);

    boolean existsByUserId(String userId);

    Page<ChallengeRow> findUncompletedChallengesByUserId(
        String userId, Pageable pageable
    );

    Page<UserChallenge> findCompletedChallengesByUserId(
        String userId, Pageable pageable
    );

    Page<ChallengeRow> findRepetitiveChallengesByUserId(
        String userId, Pageable pageable
    );

    Map<Long, UserChallenge> findRepetitiveUserChallengesMappedByStageId(
        String userId, Long challengeId
    );

    Optional<UserChallenge> findFirstBySimpleRepetitiveChallenge(String userId);

    List<UserChallenge> findAllBySimpleRepetitiveChallenge(String userId);

    void delete(UserChallenge userChallenge);
}
