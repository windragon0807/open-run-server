package io.openur.domain.user.service.login;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openur.domain.user.dto.GetUsersLoginDto;
import io.openur.domain.user.dto.SmartWalletUserInfoDto;
import io.openur.domain.user.model.User;
import io.openur.domain.user.repository.UserRepositoryImpl;
import io.openur.global.common.validation.ValidEthereumAddress;
import io.openur.global.jwt.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Slf4j(topic = "Smart Wallet Login")
@Service
public class SmartWalletService extends LoginService {
    private final JwtUtil jwtUtil;
    private final UserRepositoryImpl userRepository;

    public SmartWalletService(
        RestTemplate restTemplate,
        JwtUtil jwtUtil,
        UserRepositoryImpl userRepository
    ) {
        super(restTemplate);
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    public GetUsersLoginDto login(@ValidEthereumAddress String walletAddress, String signature) throws JsonProcessingException {
        try {
            log.info("Starting Smart Wallet login process for address: {}", walletAddress);

            User user = registerUserIfNew(SmartWalletUserInfoDto.of(walletAddress));
            log.info("User found/created successfully. User ID: {}", user.getUserId());

            String token = jwtUtil.createToken(walletAddress);
            log.debug("JWT token generated successfully");

            return new GetUsersLoginDto(
                walletAddress,
                user.getNickname(),
                token
            );
        } catch (Exception e) {
            String errorMsg = "Failed to process smart wallet login: " + e.getMessage();
            log.error(errorMsg, e);
            throw new IllegalArgumentException(errorMsg, e);
        }
    }

    @Transactional
    protected User registerUserIfNew(SmartWalletUserInfoDto smartWalletUserInfoDto) {
        String blockchainAddress = smartWalletUserInfoDto.getBlockchainAddress();
        User user = userRepository.findUser(new User(blockchainAddress));
        if (user == null) {
            User newUser = new User(blockchainAddress);
            return userRepository.save(newUser);
        } else {
            return user;
        }
    }
}
