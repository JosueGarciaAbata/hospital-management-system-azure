-- ================================
-- Database: Hospital Management System
-- ================================

-- Table: Medical Centers
CREATE TABLE medical_centers (
                                 id         BIGSERIAL PRIMARY KEY,
                                 name       VARCHAR(100) NOT NULL,
                                 city       VARCHAR(100) NOT NULL,
                                 address    VARCHAR(200) NOT NULL,
                                 created_at TIMESTAMP    NOT NULL DEFAULT now(),
                                 updated_at TIMESTAMP    NOT NULL DEFAULT now(),
                                 deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
                                 version    BIGINT       NOT NULL DEFAULT 0 -- Optimistic Locking
);

-- Unicidad (name + address) solo para registros activos (case-insensitive)
CREATE UNIQUE INDEX uq_med_centers_name_addr_active
    ON medical_centers (LOWER(name), LOWER(address))
    WHERE deleted = FALSE;

-- Índices para búsquedas frecuentes
CREATE INDEX idx_med_centers_name
    ON medical_centers (LOWER(name));

CREATE INDEX idx_med_centers_city
    ON medical_centers (LOWER(city));


-- Table: Specialties
CREATE TABLE specialties (
                             id          BIGSERIAL PRIMARY KEY,
                             name        VARCHAR(100) NOT NULL,
                             description TEXT,
                             created_at  TIMESTAMP NOT NULL DEFAULT now(),
                             updated_at  TIMESTAMP NOT NULL DEFAULT now(),
                             deleted     BOOLEAN NOT NULL DEFAULT FALSE,
                             version     BIGINT  NOT NULL DEFAULT 0 -- Optimistic Locking
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_specialties_name_active
    ON specialties (LOWER(name))
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_specialties_name
    ON specialties (LOWER(name));

-- Table: Roles
CREATE TABLE roles (
                       id BIGSERIAL PRIMARY KEY,
                       name VARCHAR(50) NOT NULL UNIQUE
);

-- Table: Users
CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       dni VARCHAR(20) NOT NULL UNIQUE,
                       email VARCHAR(50) NOT NULL UNIQUE,
                       password VARCHAR(255) NOT NULL, -- encrypted password
                       gender VARCHAR(10),
                       first_name VARCHAR(100) NOT NULL,
                       last_name VARCHAR(100) NOT NULL,
                       enabled BOOLEAN NOT NULL DEFAULT TRUE,
                       created_at TIMESTAMP    NOT NULL DEFAULT now(),
                       updated_at TIMESTAMP    NOT NULL DEFAULT now(),
                       center_id BIGINT NOT NULL,
                       CONSTRAINT fk_user_center FOREIGN KEY (center_id) REFERENCES medical_centers(id) ON DELETE CASCADE
);

-- Table: Users_Roles (join table for many-to-many)
CREATE TABLE users_roles (
                             user_id BIGINT NOT NULL,
                             role_id BIGINT NOT NULL,
                             PRIMARY KEY (user_id, role_id),
                             CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                             CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE TABLE verification_tokens (
                                     id BIGSERIAL PRIMARY KEY,
                                     user_id BIGINT NOT NULL,
                                     token VARCHAR(255) NOT NULL,
                                     used BOOLEAN NOT NULL,
                                     expiration TIMESTAMP NOT NULL,
                                     CONSTRAINT fk_verification_tokens_user
                                         FOREIGN KEY (user_id) REFERENCES users(id)
                                             ON DELETE CASCADE
);

-- Table: Doctors
DROP TABLE IF EXISTS doctors CASCADE;

CREATE TABLE doctors (
                         id           BIGSERIAL PRIMARY KEY,
                         user_id      BIGINT NOT NULL UNIQUE,
                         specialty_id BIGINT,
                         created_at   TIMESTAMP NOT NULL DEFAULT now(),
                         updated_at   TIMESTAMP NOT NULL DEFAULT now(),
                         deleted      BOOLEAN NOT NULL DEFAULT FALSE,
                         version      BIGINT  NOT NULL DEFAULT 0, -- Optimistic Locking

                         CONSTRAINT fk_doctor_user FOREIGN KEY (user_id)
                             REFERENCES users(id) ON DELETE CASCADE,

                         CONSTRAINT fk_doctor_specialty FOREIGN KEY (specialty_id)
                             REFERENCES specialties(id) ON DELETE SET NULL
);

-- Un doctor está ligado a un usuario único
CREATE UNIQUE INDEX uq_doctor_user_active
    ON doctors (user_id) WHERE deleted = FALSE;

-- Búsquedas frecuentes por especialidad
CREATE INDEX idx_doctors_specialty
    ON doctors (specialty_id) WHERE deleted = FALSE;

-- Table: Patients
CREATE TABLE patients (
                          id BIGSERIAL PRIMARY KEY,
                          dni VARCHAR(20) NOT NULL UNIQUE,
                          first_name VARCHAR(100) NOT NULL,
                          last_name VARCHAR(100) NOT NULL,
                          birth_date DATE NOT NULL,
                          gender VARCHAR(10),
                          center_id BIGINT NOT NULL,
                          deleted BOOLEAN NOT NULL DEFAULT FALSE,
                          created_at   TIMESTAMP NOT NULL DEFAULT now(),
                          updated_at   TIMESTAMP NOT NULL DEFAULT now(),
                          CONSTRAINT fk_patient_center FOREIGN KEY (center_id) REFERENCES medical_centers(id) ON DELETE CASCADE
);

-- ================================================
-- Tabla de consultas médicas particionada por centro
-- ================================================
CREATE TABLE medical_consultations (
                                       id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                                       patient_id BIGINT NOT NULL
                                           REFERENCES patients(id) ON DELETE CASCADE,
                                       doctor_id BIGINT NOT NULL
                                           REFERENCES doctors(id) ON DELETE CASCADE,
                                       center_id BIGINT NOT NULL
                                           REFERENCES medical_centers(id) ON DELETE CASCADE,
                                       "date" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       diagnosis TEXT,
                                       treatment TEXT,
                                       notes TEXT,
                                       deleted BOOLEAN NOT NULL DEFAULT FALSE,
                                       created_at TIMESTAMP NOT NULL DEFAULT now(),
                                       updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 2) Índices equivalentes a los que existían por partición
CREATE INDEX idx_mc_unp_doctor  ON medical_consultations (doctor_id);
CREATE INDEX idx_mc_unp_patient ON medical_consultations (patient_id);
CREATE INDEX idx_mc_unp_center  ON medical_consultations (center_id);
CREATE INDEX idx_mc_unp_date    ON medical_consultations ("date");

-- Habilitar extensión para hashing
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Insert default role ADMIN
INSERT INTO roles (name) VALUES ('ADMIN');
INSERT INTO roles (name) VALUES ('DOCTOR');

-- Insert default medical center (needed for FK)
INSERT INTO medical_centers (name, city, address) VALUES
                                                      ('Hospital Metropolitano', 'Quito', 'Av. Principal 123'),
                                                      ('Hospital Luis Vernaza', 'Guayaquil', 'Loja 700 y Escobedo'),
                                                      ('Hospital General Docente Ambato', 'Ambato', 'Av. Luis Pasteur y Av. Unidad Nacional');


-- Insert default admin user for Centro Médico 1 (Quito)
INSERT INTO users (dni, email, password, gender, first_name, last_name, enabled, center_id)
VALUES (
           '1500903685',
           'josuegarcab2@hotmail.com',
           crypt('admin123', gen_salt('bf')),
           'MALE',
           'System',
           'Admin',
           TRUE,
           1
       );

-- Insert default admin user for Centro Médico 2 (Guayaquil)
INSERT INTO users (dni, email, password, gender, first_name, last_name, enabled, center_id)
VALUES (
           '0912345678',
           'admin.guayaquil@example.com',
           crypt('admin123', gen_salt('bf')),
           'FEMALE',
           'System',
           'Admin',
           TRUE,
           2
       );

-- Insert default admin user for Centro Médico 3 (Ambato)
INSERT INTO users (dni, email, password, gender, first_name, last_name, enabled, center_id)
VALUES (
           '1712345678',
           'admin.ambato@example.com',
           crypt('admin123', gen_salt('bf')),
           'MALE',
           'System',
           'Admin',
           TRUE,
           3
       );

-- Link user to ADMIN role
INSERT INTO users_roles (user_id,    role_id) VALUES (1, 1);
INSERT INTO users_roles (user_id,    role_id) VALUES (2, 1);
INSERT INTO users_roles (user_id,    role_id) VALUES (3, 1);

-- Insertar usuarios doctores (dni válidos EC)
INSERT INTO users (dni, email, password, gender, first_name, last_name, enabled, center_id) VALUES
                                                                                                ('1849488182','doctor@hotmail.com',crypt('doctor123',gen_salt('bf')),'MALE','Juan','Perez',TRUE,1),
                                                                                                ('1552501056','martin.gomez@hospital.com',crypt('doctor123',gen_salt('bf')),'MALE','Martin','Gomez',TRUE,1),
                                                                                                ('0820194900','valeria.silva@hospital.com',crypt('doctor123',gen_salt('bf')),'FEMALE','Valeria','Silva',TRUE,1),
                                                                                                ('2335515249','ricardo.fuentes@hospital.com',crypt('doctor123',gen_salt('bf')),'MALE','Ricardo','Fuentes',TRUE,3),
                                                                                                ('0227907425','laura.morales@hospital.com',crypt('doctor123',gen_salt('bf')),'FEMALE','Laura','Morales',TRUE,3),
                                                                                                ('1509184485','javier.ortiz@hospital.com',crypt('doctor123',gen_salt('bf')),'MALE','Javier','Ortiz',FALSE,3),
                                                                                                ('2002638654','carolina.mendez@hospital.com',crypt('doctor123',gen_salt('bf')),'FEMALE','Carolina','Mendez',FALSE,2),
                                                                                                ('1617335060','sergio.ruiz@hospital.com',crypt('doctor123',gen_salt('bf')),'MALE','Sergio','Ruiz',FALSE,2),
                                                                                                ('1209534963','andrea.castro@hospital.com',crypt('doctor123',gen_salt('bf')),'FEMALE','Andrea','Castro',FALSE,2);

-- Asignar rol DOCTOR (id=2) a todos estos usuarios
INSERT INTO users_roles (user_id, role_id) SELECT id, 2 FROM users WHERE dni IN ('1849488182','1552501056','0820194900','2335515249','0227907425','1509184485','2002638654','1617335060','1209534963');

-- Insert default specialty
INSERT INTO specialties (name, description) VALUES
                                                ('Medicina General', 'Especialidad general para atención primaria'),
                                                ('Pediatría', 'Atención médica a niños y adolescentes'),
                                                ('Cardiología', 'Diagnóstico y tratamiento de enfermedades del corazón y sistema circulatorio'),
                                                ('Dermatología', 'Diagnóstico y tratamiento de enfermedades de la piel, cabello y uñas'),
                                                ('Ginecología', 'Atención de la salud reproductiva femenina'),
                                                ('Neurología', 'Diagnóstico y tratamiento de trastornos del sistema nervioso'),
                                                ('Psiquiatría', 'Atención de trastornos mentales y emocionales'),
                                                ('Oftalmología', 'Prevención y tratamiento de enfermedades de los ojos'),
                                                ('Ortopedia', 'Tratamiento de lesiones y enfermedades del sistema musculoesquelético'),
                                                ('Oncología', 'Prevención, diagnóstico y tratamiento del cáncer');


-- Insert doctor profile linking user to specialty (suponiendo specialty_id = 1)
INSERT INTO doctors (user_id, specialty_id, deleted)
SELECT u.id, s.id, NOT u.enabled
FROM users u
         JOIN (
    VALUES
        ('1849488182','Medicina General'),
        ('1552501056','Pediatría'),
        ('0820194900','Cardiología'),
        ('2335515249','Dermatología'),
        ('0227907425','Ginecología'),
        ('1509184485','Neurología'),
        ('2002638654','Psiquiatría'),
        ('1617335060','Oftalmología'),
        ('1209534963','Ortopedia')
) AS m(dni, spec_name) ON m.dni = u.dni
         JOIN specialties s
              ON s.name = m.spec_name
                  AND s.deleted = FALSE                -- no vincular a especialidad “borrada”
WHERE NOT EXISTS (SELECT 1 FROM doctors d WHERE d.user_id = u.id);



-- =========================
-- Insert 5 patients
-- =========================
INSERT INTO patients (dni, first_name, last_name, birth_date, gender, center_id, deleted)
VALUES
    ('patient001', 'Alice', 'Johnson', '1990-01-15', 'FEMALE', 1, FALSE),
    ('patient002', 'Bob', 'Smith', '1985-06-20', 'MALE', 1, FALSE),
    ('patient003', 'Carol', 'Davis', '2000-03-10', 'FEMALE', 2, FALSE),
    ('patient004', 'David', 'Martinez', '1995-09-05', 'MALE', 2, FALSE),
    ('patient005', 'Eva', 'Lopez', '1988-12-30', 'FEMALE', 3, FALSE);



-- =========================
-- Insert 5 medical consultation
-- =========================
INSERT INTO medical_consultations (patient_id, doctor_id, center_id, date, diagnosis, treatment, notes)
VALUES
-- Consultas paciente 1 (Alice Johnson)
(1, 1, 1, '2025-09-21 10:00:00', 'Gripe común', 'Reposo y líquidos', 'Paciente con fiebre y tos'),
(1, 1, 1, '2025-09-22 11:30:00', 'Dolor de cabeza', 'Analgésicos', 'Dolor leve, seguimiento recomendado'),

-- Consultas paciente 2 (Bob Smith)
(2, 1, 2, '2025-09-23 09:00:00', 'Chequeo rutinario', 'Ninguno', 'Todo dentro de parámetros normales'),
(2, 2, 2, '2025-09-24 14:15:00', 'Infección de garganta', 'Antibióticos', 'Revisar respuesta en 5 días'),

-- Consultas paciente 3 (Carol Davis)
(3, 3, 2, '2025-09-25 08:45:00', 'Control postoperatorio', 'Curaciones y reposo', 'Paciente estable, cicatrización correcta'),

-- Consultas paciente 4 (David Martinez)
(4, 1, 3, '2025-09-25 09:30:00', 'Dolor de espalda', 'Fisioterapia', 'Seguir tratamiento por 2 semanas'),

-- Consultas paciente 5 (Eva Lopez)
(5, 2, 3, '2025-09-26 10:00:00', 'Alergia estacional', 'Antihistamínicos', 'Revisar síntomas en 1 semana');
