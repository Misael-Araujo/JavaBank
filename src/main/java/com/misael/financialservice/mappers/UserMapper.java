package com.misael.financialservice.mappers;

import com.misael.financialservice.entities.User;
import com.misael.financialservice.dto.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getCompleteName(),
                user.getEmail(),
                user.getDocument(),
                user.getWallet(),
                user.getUserType());
    }
}
