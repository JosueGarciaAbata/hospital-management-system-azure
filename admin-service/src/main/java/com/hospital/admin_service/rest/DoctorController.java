package com.hospital.admin_service.rest;

import com.hospital.admin_service.dto.doctor.DoctorCreateRequest;
import com.hospital.admin_service.dto.doctor.DoctorRead;
import com.hospital.admin_service.dto.doctor.DoctorRegisterRequest;
import com.hospital.admin_service.dto.doctor.DoctorUpdateRequest;
import com.hospital.admin_service.mapper.DoctorMapper;
import com.hospital.admin_service.model.Doctor;
import com.hospital.admin_service.security.filters.RequireRole;
import com.hospital.admin_service.service.doctor.DoctorReadService;
import com.hospital.admin_service.service.doctor.DoctorWriteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/admin/doctors")
@RequiredArgsConstructor
@Validated
@Tag(name = "Doctores", description = "Gestión de doctores en el sistema")
public class DoctorController {

    private final DoctorMapper mapper;
    private final DoctorReadService readService;
    private final DoctorWriteService writeService;

    /* =========================
     *          READING
     * ========================= */

    @RequireRole("ADMIN")
    @GetMapping
    @Operation(summary = "Listar doctores paginados",
            description = "Devuelve una lista paginada de doctores. Opcionalmente incluye registros eliminados lógicamente.")
    public Page<DoctorRead> list(
            @Parameter(description = "Indica si se incluyen doctores eliminados", example = "false")
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            Pageable pageable) {
        return readService.findAllPage(includeDeleted, pageable);
    }

    @RequireRole("ADMIN")
    @GetMapping("/all")
    @Operation(summary = "Listar todos los doctores",
            description = "Devuelve la lista completa de doctores sin paginación. Opcionalmente incluye registros eliminados lógicamente.")
    public List<DoctorRead> listAll(
            @Parameter(description = "Indica si se incluyen doctores eliminados", example = "false")
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return readService.findAllEntities(includeDeleted).stream().toList();
    }

    @RequireRole("ADMIN")
    @GetMapping("/{id}")
    @Operation(summary = "Obtener un doctor por ID",
            description = "Devuelve un doctor por su identificador. Opcionalmente incluye registros eliminados lógicamente.")
    public DoctorRead getOne(
            @Parameter(description = "Identificador del doctor", example = "1") @PathVariable Long id,
            @Parameter(description = "Indica si se incluyen doctores eliminados", example = "false")
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return readService.findEntityById(id, includeDeleted);
    }

    @RequireRole({"ADMIN","DOCTOR"})
    @GetMapping("/by-user/{userId}")
    @Operation(summary = "Obtener un doctor por ID de usuario",
            description = "Devuelve un doctor usando el identificador del usuario asociado.")
    public DoctorRead getByUserId(
            @Parameter(description = "Identificador del usuario asociado al doctor", example = "100")
            @PathVariable Long userId) {
        return readService.findByUserId(userId);
    }

    @RequireRole("ADMIN")
    @GetMapping("/by-specialty/{specialtyId}")
    @Operation(summary = "Listar doctores por especialidad",
            description = "Devuelve una lista paginada de doctores filtrados por especialidad.")
    public Page<DoctorRead> listBySpecialty(
            @Parameter(description = "Identificador de la especialidad", example = "5")
            @PathVariable Long specialtyId,
            Pageable pageable) {
        return readService.findBySpecialty(specialtyId, pageable);
    }

    @RequireRole("ADMIN")
    @GetMapping("/exists-by-user/{userId}")
    public ResponseEntity<Void> existsByUserId(@PathVariable Long userId) {
        boolean exists = readService.existsByUserId(userId);
        return exists ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    /* =========================
     *          WRITING
     * ========================= */

    @RequireRole("ADMIN")
    @PostMapping("/register")
    @Operation(summary = "Registrar un doctor y un usuario",
            description = "Crea un nuevo usuario con rol DOCTOR en el servicio de autenticación y un doctor en el servicio local.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Doctor registrado correctamente"),
            @ApiResponse(responseCode = "400", description = "Error en el cuerpo de la solicitud"),
            @ApiResponse(responseCode = "403", description = "Prohibido - se requiere rol ADMIN")
    })
    public ResponseEntity<DoctorRead> register(
            @Valid @RequestBody DoctorRegisterRequest body) {
        var saved = writeService.registerDoctor(body);
        var dto   = mapper.toRead(saved);
        return ResponseEntity.created(URI.create("/admin/doctors/" + dto.id())).body(dto);
    }

    @RequireRole("ADMIN")
    @PostMapping
    @Operation(summary = "Crear un doctor",
            description = "Crea un nuevo doctor asociado a una especialidad.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Doctor creado correctamente"),
            @ApiResponse(responseCode = "400", description = "Error en el cuerpo de la solicitud")
    })
    public ResponseEntity<DoctorRead> create(
            @Valid @RequestBody DoctorCreateRequest body) {
        Doctor entity = mapper.toEntity(body);
        Doctor saved  = writeService.create(entity, body.specialtyId());
        URI location  = URI.create("/admin/doctors/" + saved.getId());
        return ResponseEntity.created(location).body(mapper.toRead(saved));
    }

    @RequireRole("ADMIN")
    @PutMapping("/{id}")
    @Operation(summary = "Actualizar un doctor",
            description = "Actualiza un doctor existente junto con su especialidad.")
    public DoctorRead update(
            @Parameter(description = "Identificador del doctor", example = "1") @PathVariable Long id,
            @Valid @RequestBody DoctorUpdateRequest body) {
        Doctor incoming = new Doctor();
        mapper.updateEntityFromDto(body, incoming);
        Doctor saved = writeService.update(id, incoming, body.specialtyId());
        return mapper.toRead(saved);
    }

    @RequireRole("ADMIN")
    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un doctor (borrado lógico)",
            description = "Marca un doctor como eliminado sin eliminarlo físicamente de la base de datos.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Doctor eliminado correctamente"),
            @ApiResponse(responseCode = "404", description = "Doctor no encontrado")
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "Identificador del doctor", example = "1")
            @PathVariable Long id) {
        writeService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
