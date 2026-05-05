package io.openur.domain.userchallenge.dto;

import io.openur.domain.challenge.model.ChallengeStage;
import io.openur.domain.userchallenge.model.UserChallenge;

public record ChallengeRow(ChallengeStage stage, UserChallenge userChallenge) {
}
