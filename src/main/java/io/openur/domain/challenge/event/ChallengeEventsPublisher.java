package io.openur.domain.challenge.event;

import io.openur.domain.userchallenge.model.UserChallenge;
import io.openur.domain.userchallenge.repository.UserChallengeRepository;
import io.openur.domain.userchallenge.service.UserChallengeInitializer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChallengeEventsPublisher {

    private final ApplicationEventPublisher publisher;
    private final UserChallengeRepository userChallengeRepository;
    private final UserChallengeInitializer userChallengeInitializer;

    @Transactional
    public void publishChallengeCheck(String userId) {
        userChallengeInitializer.ensureInitialized(userId);

        userChallengeRepository.findFirstBySimpleRepetitiveChallenge(userId)
            .ifPresent(userChallenge -> {
                if (userChallenge.getCurrentCount() + 1 < userChallenge.getChallengeStage().getConditionAsCount())
                    publisher.publishEvent(new OnRaise(List.of(userChallenge)));
                else
                    publisher.publishEvent(new OnEvolution(List.of(userChallenge)));
            });
    }

    @Transactional
    public void publishBulkChallengeCheck(String userId) {
        userChallengeInitializer.ensureInitialized(userId);

        List<UserChallenge> all = userChallengeRepository.findAllBySimpleRepetitiveChallenge(userId);
        if (all.isEmpty()) return;

        List<UserChallenge> toRaise = all.stream()
            .filter(uc -> uc.getCurrentCount() + 1 < uc.getChallengeStage().getConditionAsCount())
            .toList();
        List<UserChallenge> toEvolve = all.stream()
            .filter(uc -> uc.getCurrentCount() + 1 >= uc.getChallengeStage().getConditionAsCount())
            .toList();

        if (!toRaise.isEmpty()) publisher.publishEvent(new OnRaise(toRaise));
        if (!toEvolve.isEmpty()) publisher.publishEvent(new OnEvolution(toEvolve));
    }
}
