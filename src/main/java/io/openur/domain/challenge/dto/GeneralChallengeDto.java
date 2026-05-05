package io.openur.domain.challenge.dto;

import io.openur.domain.challenge.enums.ChallengeType;
import io.openur.domain.challenge.enums.CompletedType;
import io.openur.domain.challenge.model.Challenge;
import io.openur.domain.challenge.model.ChallengeStage;
import io.openur.domain.userchallenge.model.UserChallenge;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class GeneralChallengeDto {
    private Long challengeId;
    private Long userChallengeId;
    private String challengeName;
    private LocalDateTime completedDate;
    private String challengeDescription;
    private Integer currentCount;
    private Integer conditionCount;
    private LocalDateTime conditionDate;
    private String conditionText;
    private ChallengeType challengeType;
    private CompletedType completedType;
    private Integer stageCount;
    private float progressStat = 0.0f;
    private boolean accomplished;
    private boolean nftCompleted;

    public GeneralChallengeDto(UserChallenge userChallenge) {
        this(userChallenge.getChallengeStage(), userChallenge);
    }

    public GeneralChallengeDto(ChallengeStage challengeStage, UserChallenge userChallenge) {
        if (userChallenge != null) {
            this.userChallengeId = userChallenge.getUserChallengeId();
            this.currentCount = userChallenge.getCurrentCount();
            this.completedDate = userChallenge.getCompletedDate();
            this.accomplished = userChallenge.getCompletedDate() != null;
            this.nftCompleted = userChallenge.getNftCompleted();
            this.progressStat = userChallenge.getCurrentProgress();
        } else {
            this.currentCount = 0;
        }

        this.conditionCount = challengeStage.getConditionAsCount();
        this.stageCount = challengeStage.getStageNumber();

        Challenge challenge = challengeStage.getChallenge();
        this.challengeId = challenge.getChallengeId();
        this.challengeName = challenge.getChallengeName();
        this.challengeDescription = challenge.getChallengeDescription();
        this.completedType = challenge.getCompletedType();
        this.conditionDate = challenge.getCompletedConditionDate();
        this.conditionText = challenge.getCompletedConditionText();
        this.challengeType = challenge.getChallengeType();
    }

    @Getter
    @RequiredArgsConstructor
    public static class OnRaise {
        // 추후, 같은 조건이거나 혹은 충족 조건인 여러개의 도전과제가 인덱싱 되었을때를 대비
        private final List<UserChallenge> userChallenges;
    }

    @Getter
    @RequiredArgsConstructor
    public static class OnEvolution {
        private final List<UserChallenge> userChallenges;
    }

    @Getter
    @RequiredArgsConstructor
    public static class OnIssue {
        private final UserChallenge userChallenge;
    }

    @Getter
    @RequiredArgsConstructor
    public static class OnDate {
        private final List<UserChallenge> userChallenges;
    }

    @Getter
    @RequiredArgsConstructor
    public static class OnPlace {
        private final List<UserChallenge> userChallenges;
        private final String location;
    }

    @Getter
    @RequiredArgsConstructor
    public static class OnWearing {

        private final List<UserChallenge> userChallenges;
        private final String wearingId; // TODO: NFT 정의할 떄 같이 정의 필요
    }
}
