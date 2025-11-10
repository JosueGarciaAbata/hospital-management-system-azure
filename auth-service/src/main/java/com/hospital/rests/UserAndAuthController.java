package com.hospital.rests;

import com.hospital.dtos.*;
import com.hospital.entities.User;
import com.hospital.exceptions.SelfDeletionNotAllowedException;
import com.hospital.mappers.UserMapper;
import com.hospital.security.aop.RequireRole;
import com.hospital.services.PasswordResetService;
import com.hospital.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
@Tag(name = "Usuarios y Autenticación", description = "Gestión de usuarios y endpoints de autenticación/recuperación de contraseña")
public class UserAndAuthController {

    private final UserService service;
    private final UserMapper mapper;
    private final PasswordResetService passwordResetService;

    @GetMapping("/ok1")
    public String health() {
        return "ok";
    }

    @GetMapping("/ok2")
    public String testing() {
        return "ok";
    }

    /* =========================
     *          USERS
     * ========================= */

    @RequireRole("ADMIN")
    @GetMapping("/users/all-testing")
    public ResponseEntity<List<UserResponse>> findAllTesting(){
        return ResponseEntity.ok(service.findAllTesting().stream().map(mapper::toUserResponse).collect(Collectors.toList()));
    }

    @RequireRole("ADMIN")
    @GetMapping("/users")
    @Operation(
            summary = "Listar usuarios paginados",
            description = "Devuelve una página de usuarios. Permite ordenar por el campo especificado."
    )
    public ResponseEntity<Page<UserResponse>> findAllUsers(
            @RequestHeader("X-User-Id") String userId,
            @Parameter(description = "Número de página (0-indexado)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Campo por el cual ordenar", example = "id")
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy));
        return ResponseEntity.ok(service.findAllExludingUser(Long.parseLong(userId), pageable, includeDeleted));
    }

    @GetMapping("/users/me")
    @Operation(
            summary = "Obtener el usuario actual",
            description = "Devuelve la información del usuario autenticado. El Gateway inyecta los headers X-User-Id / X-Center-Id."
    )
    public ResponseEntity<UserResponse> me(
            @Parameter(description = "Identificador del usuario autenticado", example = "101")
            @RequestHeader("X-User-Id") String userId) {
        User user = service.findUserById(Long.parseLong(userId), true);
        return ResponseEntity.ok(mapper.toUserResponse(user));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Obtener un usuario por ID")
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(description = "Identificador del usuario", example = "25")
            @PathVariable Long id,
            @Parameter(description = "Filtrar por usuarios habilitados (por defecto true)", example = "true")
            @RequestParam(defaultValue = "true") boolean enabled) {

        User user = service.findUserById(id, enabled);
        return ResponseEntity.ok(mapper.toUserResponse(user));
    }

    @GetMapping("/users/by-center/{id}")
    @Operation(summary = "Obtener usuario por ID de centro",
            description = "Busca el usuario asociado a un centro médico. Puede incluir deshabilitados.")
    public ResponseEntity<UserResponse> getUserByCenterId(
            @Parameter(description = "Identificador del centro médico", example = "5")
            @PathVariable Long id,
            @Parameter(description = "Si es true, incluye usuarios deshabilitados", example = "false")
            @RequestParam(name = "includeDisabled", defaultValue = "false") boolean includeDisabled) {

        UserResponse response = mapper.toUserResponse(service.findUserByCenterId(id, includeDisabled));
        return ResponseEntity.ok(response);
    }

    @RequestMapping(value = "/users/by-center/{id}/exists", method = RequestMethod.HEAD)
    @Operation(summary = "Verificar existencia de usuario por centro (HEAD)",
            description = "Devuelve 204 No Content si existe, 404 Not Found si no existe.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Existe un usuario para el centro indicado"),
            @ApiResponse(responseCode = "404", description = "No existe usuario para el centro indicado")
    })
    public ResponseEntity<Void> existsUserByCenter(
            @Parameter(description = "Identificador del centro médico", example = "5")
            @PathVariable Long id,
            @Parameter(description = "Si es true, incluye usuarios deshabilitados en la verificación", example = "false")
            @RequestParam(name = "includeDisabled", defaultValue = "false") boolean includeDisabled) {

        boolean exists = service.existsUserByCenterId(id, includeDisabled);
        return exists ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @RequireRole("ADMIN")
    @PostMapping("/register")
    @Operation(summary = "Registrar un nuevo usuario")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario registrado correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos en la solicitud")
    })
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse response = mapper.toUserResponse(this.service.register(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @RequireRole("ADMIN")
    @PutMapping("/users/{id}")
    @Operation(summary = "Actualizar un usuario existente")
    public ResponseEntity<UserResponse> updateUser(
            @Parameter(description = "Identificador del usuario a actualizar", example = "25")
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request) {
        UserResponse updatedUser = mapper.toUserResponse(this.service.update(id, request));
        return ResponseEntity.status(HttpStatus.OK).body(updatedUser);
    }

    @RequireRole("ADMIN")
    @DeleteMapping("/c/{id}")
    @Operation(summary = "Eliminar o deshabilitar un usuario",
            description = "Si `hard=true`, elimina físicamente el usuario; si no, lo deshabilita (borrado lógico).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Operación realizada correctamente")
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "Identificador del usuario", example = "25")
            @PathVariable Long id,
            @Parameter(description = "Eliminación física si es true; deshabilitar si es false", example = "false")
            @RequestParam(name = "hard", defaultValue = "false") boolean hard) {
        // Chequeando si el usuario es un doctor. Si es asi, no se puede eliminar
        this.service.validateDoctorAssigned(id);

        if (hard) {
            this.service.hardDeleteUser(id);
        } else {
            this.service.disableUser(id);
        }

        return ResponseEntity.noContent().build();
    }

    @RequireRole("ADMIN")
    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar o deshabilitar un usuario",
            description = "Si `hard=true`, elimina físicamente el usuario; si no, lo deshabilita (borrado lógico).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Operación realizada correctamente")
    })
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "Identificador del usuario", example = "25")
            @PathVariable Long id,
            @Parameter(description = "Eliminación física si es true; deshabilitar si es false", example = "false")
            @RequestParam(name = "hard", defaultValue = "false") boolean hard) {

        if (hard) {
            this.service.hardDeleteUser(id);
        } else {
            this.service.disableUser(id);
        }

        return ResponseEntity.noContent().build();
    }

    /* =========================
     *   PASSWORD RECOVERY
     * ========================= */

    @PostMapping("/request-reset")
    @Operation(summary = "Solicitar restablecimiento de contraseña",
            description = "Envía un token de restablecimiento al correo o identificador proporcionado.")
    public ResponseEntity<Void> requestReset(@RequestBody RequestPasswordRequest input) {
        passwordResetService.requestPasswordReset(input.getInput());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Restablecer contraseña",
            description = "Aplica el restablecimiento de contraseña utilizando el token recibido previamente.")
    public ResponseEntity<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok().build();
    }
}
