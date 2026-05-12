package polight.server.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import polight.server.domain.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
}
