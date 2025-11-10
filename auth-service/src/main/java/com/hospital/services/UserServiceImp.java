package com.hospital.services;

import com.hospital.dtos.CreateUserRequest;
import com.hospital.dtos.MedicalCenterDto;
import com.hospital.dtos.UpdateUserRequest;
import com.hospital.dtos.UserResponse;
import com.hospital.entities.Role;
import com.hospital.entities.User;
import com.hospital.enums.GenderType;
import com.hospital.exceptions.*;
import com.hospital.feign.AdminServiceWrapper;
import com.hospital.mappers.UserMapper;
import com.hospital.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImp implements UserService {

    private final UserRepository repository;
    private final AdminServiceWrapper wrapper;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper mapper;

    @Override
    public Page<UserResponse> findAll(Pageable pageable, boolean includeDeleted) {
        // Traer usuarios con paginación
        Page<User> users;

        if (includeDeleted) {
            users = repository.findAllIncludingDeleted(pageable);
        } else {
            users = repository.findAll(pageable);
        }

        // Extraer los centerId únicos
        List<Long> centerIds = users.stream()
                .map(User::getCenterId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // Llamar al admin-service en batch
        List<MedicalCenterDto> centers = wrapper.getCentersById(centerIds, includeDeleted);

        // Convertir a Map<Long, String> para acceso rápido
        Map<Long, String> centersMap = centers.stream()
                .collect(Collectors.toMap(MedicalCenterDto::getId, MedicalCenterDto::getName));

        // Mapear usuarios a UserResponse con el nombre del centro
        return users.map(user -> {
            UserResponse dto = mapper.toUserResponse(user);
            String centerName = centersMap.getOrDefault(user.getCenterId(), "Centro desconocido");
            dto.setCenterName(centerName);
            return dto;
        });
    }

    @Override
    public Page<UserResponse> findAllExludingUser(Long userId, Pageable pageable, boolean includeDeleted) {
        // Traer usuarios con paginación
        Page<User> users;

        if (includeDeleted) {
            users = repository.findAllIncludingDeletedExcludingUser(userId, pageable);
        } else {
            users = repository.findAllExcludingUser(userId, pageable);
        }

        // Extraer los centerId únicos
        List<Long> centerIds = users.stream()
                .map(User::getCenterId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // Llamar al admin-service en batch
        List<MedicalCenterDto> centers = wrapper.getCentersById(centerIds, includeDeleted);

        // Convertir a Map<Long, String> para acceso rápido
        Map<Long, String> centersMap = centers.stream()
                .collect(Collectors.toMap(MedicalCenterDto::getId, MedicalCenterDto::getName));

        // Mapear usuarios a UserResponse con el nombre del centro
        return users.map(user -> {
            UserResponse dto = mapper.toUserResponse(user);
            String centerName = centersMap.getOrDefault(user.getCenterId(), "Centro desconocido");
            dto.setCenterName(centerName);
            return dto;
        });
    }

    @Override
    public List<User> findAllTesting() {
        return this.repository.findAllTesting();
    }

    @Override
    public User register(CreateUserRequest request) {
        User user = mapper.toUser(request);

        if (repository.existsByUsername(user.getUsername())) {
            throw new DniAlreadyExistsException("Ya existe un usuario con DNI " + user.getUsername());
        }

        if (repository.existsByEmail(user.getEmail())) {
            throw new EmailAlreadyExistsException("Ya existe un usuario asoociado con ese email  " + user.getEmail());
        }

        Long centerId = user.getCenterId();
        ResponseEntity<Void> response = wrapper.validateCenterId(centerId);
        if (response.getStatusCode().is5xxServerError()) {
            throw new ServiceUnavailableException("El servicio de administración no está disponible en este momento. Intente nuevamente más tarde.");
        }
        if (response.getStatusCode().is4xxClientError()) {
            throw new CenterIdNotFoundException("El ID del centro no existe: " + centerId);
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        Set<Role> savedRoles = user.getRoles().stream()
                .map(role -> roleService.findRoleByDni(role.getName()))
                .collect(Collectors.toSet());
        user.setRoles(savedRoles);

        return repository.save(user);
    }

    @Override
    public User findUserByDni(String dni) {
        return repository.findByUsernameOrEmail(dni)
                .orElseThrow(() -> new UserByDniNotFoundException(dni));
    }

    @Override
    public User findUserById(Long id, boolean enabled) {
        if (enabled) {
            return repository.findUserById(id)
                    .orElseThrow(() -> new UserNotFoundException(id));
        } else {
            return repository.findUserByIdIncludingDisabled(id)
                    .orElseThrow(() -> new UserNotFoundException(id));
        }
    }

    @Override
    public User findUserByCenterId(Long centerId, boolean includeDisabled) {
        if (includeDisabled) {
            return repository.findFirstByCenterId(centerId)
                    .orElseThrow(() -> new UserNotFoundException(centerId));
        }
        return repository.findFirstActiveByCenterId(centerId)
                .orElseThrow(() -> new UserNotFoundException(centerId));
    }

    @Override
    public boolean existsUserByCenterId(Long centerId, boolean includeDisabled) {
        return includeDisabled
                ? repository.existsByCenterId(centerId)
                : repository.existsActiveByCenterId(centerId);
    }

    @Override
    public User update(Long id, UpdateUserRequest request) {
        User user = repository.findById(id)
                .orElseThrow(() -> new UserNotFoundException( id));

        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getGender() != null) user.setGender(GenderType.valueOf(request.getGender()));

        return repository.save(user);
    }

    @Override
    public void updatePassword(Long id, String newPass) {
        User user = repository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        if (passwordEncoder.matches(newPass, user.getPassword())) {
            throw new IllegalArgumentException("La nueva contraseña no puede ser igual a la anterior.");
        }

        user.setPassword(passwordEncoder.encode(newPass));
        repository.save(user);
    }

    @Override
    public void disableUser(Long id) {
        User user = repository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        repository.delete(user);
    }

    @Override
    @Transactional
    public void hardDeleteUser(Long id) {
        User user = repository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        repository.hardDeleteById(user.getId());
    }

    @Override
    public User findByUsername(String username) {
        return this.findByUsername(username);
    }

    @Override
    public void validateDoctorAssigned(Long userId) {

        ResponseEntity<Void> response = this.wrapper.existsByUserId(userId);
        log.info("Estado response {}", response.getStatusCode());

        if (response.getStatusCode().is4xxClientError()) {
            // Caso esperado: no existe doctor asociado, continuar sin error
            return;
        }

        if (response.getStatusCode().is5xxServerError()) {
            throw new ServiceUnavailableException("El servicio de administración no está disponible en este momento. Intente nuevamente más tarde.");
        }

        if (response.getStatusCode().is2xxSuccessful()) {
            throw new DoctorAssignedException("El usuario tiene un doctor asignado y no se puede eliminar.");
        }
    }

}
