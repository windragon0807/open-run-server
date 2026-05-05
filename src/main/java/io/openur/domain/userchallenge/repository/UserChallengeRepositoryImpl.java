package io.openur.domain.userchallenge.repository;

import static io.openur.domain.challenge.entity.QChallengeEntity.challengeEntity;
import static io.openur.domain.challenge.entity.QChallengeStageEntity.challengeStageEntity;
import static io.openur.domain.userchallenge.entity.QUserChallengeEntity.userChallengeEntity;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.openur.domain.challenge.entity.ChallengeStageEntity;
import io.openur.domain.challenge.entity.QChallengeStageEntity;
import io.openur.domain.challenge.enums.ChallengeType;
import io.openur.domain.challenge.model.ChallengeStage;
import io.openur.domain.userchallenge.dto.ChallengeRow;
import io.openur.domain.userchallenge.entity.QUserChallengeEntity;
import io.openur.domain.userchallenge.entity.UserChallengeEntity;
import io.openur.domain.userchallenge.model.UserChallenge;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserChallengeRepositoryImpl implements UserChallengeRepository {

    private final UserChallengeJpaRepository userChallengeJpaRepository;
    private final JPAQueryFactory queryFactory;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public UserChallenge save(UserChallenge userChallenge) {
        return UserChallenge.from(
            userChallengeJpaRepository.save(userChallenge.toEntity()));
    }

    @Transactional
    @Override
    public void bulkInsertUserChallenges(List<UserChallenge> userChallenges) {
        int count = 0;
        int batchSize = 100;

        for (UserChallenge userChallenge : userChallenges) {
            UserChallengeEntity entity = userChallenge.toEntity();

            entityManager.persist(entity);
            count++;

            if (count % batchSize == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }

        entityManager.flush();
        entityManager.clear();
    }

    @Override
    @Transactional
    public void bulkIncrementCount(List<Long> userChallengeIds) {
        if (userChallengeIds.isEmpty()) {
            return;
        }

        queryFactory
            .update(userChallengeEntity)
            .set(userChallengeEntity.currentCount, userChallengeEntity.currentCount.add(1))
            .where(userChallengeEntity.userChallengeId.in(userChallengeIds))
            .execute();

        entityManager.flush();
        entityManager.clear();
    }

    @Override
    @Transactional
    public void bulkUpdateCompletedChallenges(List<Long> userChallengeIds) {
        if (userChallengeIds.isEmpty()) {
            return;
        }

        queryFactory
            .update(userChallengeEntity)
            .set(userChallengeEntity.currentCount, userChallengeEntity.currentCount.add(1))
            .set(userChallengeEntity.completedDate, LocalDateTime.now())
            .set(userChallengeEntity.nftCompleted, false)
            .where(userChallengeEntity.userChallengeId.in(userChallengeIds))
            .execute();

        entityManager.flush();
        entityManager.clear();
    }

    @Override
    @Transactional
    public void markNftCompleted(Long userChallengeId) {
        queryFactory
            .update(userChallengeEntity)
            .set(userChallengeEntity.nftCompleted, true)
            .where(userChallengeEntity.userChallengeId.eq(userChallengeId))
            .execute();

        entityManager.flush();
        entityManager.clear();
    }

    @Override
    public boolean existsByUserId(String userId) {
        Integer one = queryFactory
            .selectOne()
            .from(userChallengeEntity)
            .where(userChallengeEntity.userEntity.userId.eq(userId))
            .fetchFirst();
        return one != null;
    }

    @Override
    public Page<ChallengeRow> findUncompletedChallengesByUserId(
        String userId,
        Pageable pageable
    ) {
        BooleanExpression stageIsCurrent = buildStageIsCurrentForUser(userId);
        BooleanExpression typeFilter = challengeStageEntity.challengeEntity.challengeType
            .notIn(ChallengeType.hidden, ChallengeType.repetitive);

        return findChallengeRowsByConditions(userId, pageable, stageIsCurrent, typeFilter);
    }

    @Override
    public Page<UserChallenge> findCompletedChallengesByUserId(
        String userId,
        Pageable pageable
    ) {
        BooleanExpression conditions = buildNftIssuableConditions(userId);

        List<UserChallengeEntity> content = queryFactory
            .selectFrom(userChallengeEntity)
            .leftJoin(userChallengeEntity.challengeStageEntity, challengeStageEntity)
            .fetchJoin()
            .leftJoin(challengeStageEntity.challengeEntity, challengeEntity)
            .fetchJoin()
            .where(conditions)
            .orderBy(
                userChallengeEntity.completedDate.asc(),
                userChallengeEntity.userChallengeId.asc()
            )
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        if (content.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        JPAQuery<Long> countQuery = queryFactory
            .select(userChallengeEntity.count())
            .from(userChallengeEntity)
            .where(conditions);

        List<UserChallenge> result = content.stream()
            .map(UserChallenge::from)
            .collect(Collectors.toList());

        return PageableExecutionUtils.getPage(result, pageable, countQuery::fetchOne);
    }

    @Override
    public Page<ChallengeRow> findRepetitiveChallengesByUserId(
        String userId,
        Pageable pageable
    ) {
        if (!StringUtils.hasText(userId)) {
            return Page.empty(pageable);
        }

        BooleanExpression stageIsCurrent = buildStageIsCurrentForUser(userId);
        BooleanExpression typeFilter = challengeStageEntity.challengeEntity.challengeType
            .eq(ChallengeType.repetitive);

        return findChallengeRowsByConditions(userId, pageable, stageIsCurrent, typeFilter);
    }

    private Page<ChallengeRow> findChallengeRowsByConditions(
        String userId,
        Pageable pageable,
        BooleanExpression stageIsCurrent,
        BooleanExpression typeFilter
    ) {
        List<ChallengeStageEntity> stages = queryFactory
            .selectFrom(challengeStageEntity)
            .distinct()
            .innerJoin(challengeStageEntity.challengeEntity, challengeEntity)
            .fetchJoin()
            .leftJoin(userChallengeEntity)
            .on(
                userChallengeEntity.challengeStageEntity.stageId.eq(challengeStageEntity.stageId),
                userChallengeEntity.userEntity.userId.eq(userId)
            )
            .where(stageIsCurrent, typeFilter)
            .orderBy(
                Expressions.cases()
                    .when(userChallengeEntity.currentCount.coalesce(0)
                        .goe(challengeStageEntity.conditionAsCount))
                    .then(0).otherwise(1).asc(),
                userChallengeEntity.currentProgress.coalesce(0.0f).desc(),
                challengeStageEntity.challengeEntity.challengeId.asc(),
                challengeStageEntity.stageId.asc()
            )
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        if (stages.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        Map<Long, UserChallengeEntity> ucByStageId = fetchUserChallengeMap(userId, stages);

        JPAQuery<Long> countQuery = queryFactory
            .select(challengeStageEntity.countDistinct())
            .from(challengeStageEntity)
            .innerJoin(challengeStageEntity.challengeEntity, challengeEntity)
            .where(stageIsCurrent, typeFilter);

        List<ChallengeRow> result = stages.stream()
            .map(stage -> new ChallengeRow(
                ChallengeStage.from(stage),
                Optional.ofNullable(ucByStageId.get(stage.getStageId()))
                    .map(UserChallenge::from)
                    .orElse(null)
            ))
            .collect(Collectors.toList());

        return PageableExecutionUtils.getPage(result, pageable, countQuery::fetchOne);
    }

    @Override
    public Map<Long, UserChallenge> findRepetitiveUserChallengesMappedByStageId(
        String userId,
        Long challengeId
    ) {
        List<UserChallengeEntity> entities = queryFactory
            .selectFrom(userChallengeEntity)
            .leftJoin(userChallengeEntity.challengeStageEntity, challengeStageEntity)
            .fetchJoin()
            .leftJoin(challengeStageEntity.challengeEntity, challengeEntity)
            .fetchJoin()
            .where(
                userChallengeEntity.userEntity.userId.eq(userId),
                challengeEntity.challengeId.eq(challengeId),
                userChallengeEntity.nftCompleted.isFalse()
            )
            .fetch();

        if (entities.isEmpty()) {
            return Collections.emptyMap();
        }

        return entities.stream()
            .map(UserChallenge::from)
            .collect(Collectors.toMap(
                userChallenge -> userChallenge.getChallengeStage().getStageId(),
                Function.identity()
            ));
    }

    @Override
    public Optional<UserChallenge> findFirstBySimpleRepetitiveChallenge(String userId
    ) {
        return Optional.ofNullable(
            queryFactory
                .selectFrom(userChallengeEntity)
                .join(userChallengeEntity.challengeStageEntity, challengeStageEntity)
                .join(challengeStageEntity.challengeEntity, challengeEntity)
                .where(
                    userChallengeEntity.userEntity.userId.eq(userId),
                    userChallengeEntity.completedDate.isNull(),
                    challengeEntity.conditionAsDate.isNull(),
                    challengeEntity.conditionAsText.isNull()
                )
                .orderBy(challengeStageEntity.stageNumber.asc())
                .fetchFirst()
        ).map(UserChallenge::from);
    }

    @Override
    public List<UserChallenge> findAllBySimpleRepetitiveChallenge(String userId) {
        return queryFactory
            .selectFrom(userChallengeEntity)
            .join(userChallengeEntity.challengeStageEntity, challengeStageEntity)
            .join(challengeStageEntity.challengeEntity, challengeEntity)
            .where(
                userChallengeEntity.userEntity.userId.eq(userId),
                userChallengeEntity.completedDate.isNull(),
                challengeEntity.conditionAsDate.isNull(),
                challengeEntity.conditionAsText.isNull()
            )
            .orderBy(challengeStageEntity.stageNumber.asc())
            .fetch()
            .stream()
            .map(UserChallenge::from)
            .toList();
    }

    @Override
    public void delete(UserChallenge userChallenge) {
        userChallengeJpaRepository.delete(userChallenge.toEntity());
    }

    /**
     * "표시할 stage" 결정 규칙:
     * (A) 해당 user의 미완료 user_challenge가 이 stage에 있으면 이 stage가 표시 대상이다, 또는
     * (B) 해당 user가 이 challenge에 user_challenge를 하나도 가지지 않았고 (= 신규 유저),
     *     이 stage가 해당 challenge의 stage_number 최소값을 가진 stage이면 표시 대상이다.
     *
     * 결과적으로 challenge 1개당 최대 1개의 stage만 통과:
     * - 신규 유저: stage 1
     * - 진행 중 유저: 진행 중인 stage
     * - 모든 stage가 완료된 challenge: 통과 안 함 (완료 탭 영역)
     */
    private BooleanExpression buildStageIsCurrentForUser(String userId) {
        QUserChallengeEntity ucSub = new QUserChallengeEntity("ucSub");
        QChallengeStageEntity stageSub = new QChallengeStageEntity("stageSub");
        QChallengeStageEntity stageMinSub = new QChallengeStageEntity("stageMinSub");

        BooleanExpression hasIncompleteOnThisStage = JPAExpressions
            .selectOne()
            .from(ucSub)
            .where(
                ucSub.challengeStageEntity.stageId.eq(challengeStageEntity.stageId),
                ucSub.userEntity.userId.eq(userId),
                ucSub.completedDate.isNull()
            )
            .exists();

        BooleanExpression hasNoUcForThisChallenge = JPAExpressions
            .selectOne()
            .from(ucSub)
            .join(ucSub.challengeStageEntity, stageSub)
            .where(
                stageSub.challengeEntity.challengeId.eq(challengeStageEntity.challengeEntity.challengeId),
                ucSub.userEntity.userId.eq(userId)
            )
            .notExists();

        BooleanExpression isMinStage = challengeStageEntity.stageNumber.eq(
            JPAExpressions
                .select(stageMinSub.stageNumber.min())
                .from(stageMinSub)
                .where(stageMinSub.challengeEntity.challengeId.eq(challengeStageEntity.challengeEntity.challengeId))
        );

        return hasIncompleteOnThisStage.or(hasNoUcForThisChallenge.and(isMinStage));
    }

    private Map<Long, UserChallengeEntity> fetchUserChallengeMap(
        String userId, List<ChallengeStageEntity> stages
    ) {
        List<Long> stageIds = stages.stream()
            .map(ChallengeStageEntity::getStageId)
            .toList();
        return queryFactory
            .selectFrom(userChallengeEntity)
            .where(
                userChallengeEntity.challengeStageEntity.stageId.in(stageIds),
                userChallengeEntity.userEntity.userId.eq(userId)
            )
            .fetch()
            .stream()
            .collect(Collectors.toMap(
                uc -> uc.getChallengeStageEntity().getStageId(),
                Function.identity(),
                (a, b) -> a
            ));
    }

    private BooleanExpression buildNftIssuableConditions(String userId) {
        return userChallengeEntity.userEntity.userId.eq(userId)
            .and(userChallengeEntity.completedDate.isNotNull())
            .and(userChallengeEntity.nftCompleted.isFalse());
    }
}
