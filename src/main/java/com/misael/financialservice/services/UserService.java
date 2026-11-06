package com.misael.financialservice.services;

import com.misael.financialservice.entities.User;
import com.misael.financialservice.dto.UserResponse;
import com.misael.financialservice.exceptions.ApiException;
import com.misael.financialservice.mappers.UserMapper;
import com.misael.financialservice.repositories.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id, Long requesterId, boolean administrator) {
        if (!administrator && !id.equals(requesterId)) {
            throw ApiException.forbidden("You can only view your own account.");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User was not found."));
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listAll(Pageable pageable) {
        Pageable boundedPage = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
                pageable.getSort());
        return userRepository.findAll(boundedPage).map(userMapper::toResponse);
    }
}
