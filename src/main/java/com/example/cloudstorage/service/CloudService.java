package com.example.cloudstorage.service;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.example.cloudstorage.dto.CloudFileDto;
import com.example.cloudstorage.entity.CloudFileEntity;
import com.example.cloudstorage.entity.UserEntity;
import com.example.cloudstorage.repository.CloudRepository;
import com.example.cloudstorage.security.JWTToken;
import com.example.cloudstorage.util.CloudManager;

import javax.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CloudService {

    private final CloudManager cloudManager;
    private final JWTToken jwtToken;
    private final CloudRepository cloudRepository;

    @SneakyThrows
    @Transactional()
    public boolean uploadFile(MultipartFile multipartFile, String fileName) {
        Optional<CloudFileEntity> cloudFile = getCloudFileEntity(fileName);
        byte [] byteArr = multipartFile.getBytes();
        long multLong = multipartFile.getSize();
        if (cloudFile.isPresent()) {
            log.info("Такой файл имеется в БД, начинаем переименовывать {}", fileName);
            String renameFile = fileName;
            var indexPoint = fileName.indexOf(".");
            for (int i = 1; i < Integer.MAX_VALUE; i++) {
                renameFile = String.format(fileName.substring(0, indexPoint) + " (%d)" + fileName.substring(indexPoint), i);
                cloudFile = getCloudFileEntity(renameFile);
                if (cloudFile.isEmpty()) {
                    break;
                }
            }
            fileName = renameFile;
        }

        log.info("Такого файла нет, можно начинать запись {}", fileName);
        CloudFileEntity cloudFileEntity = CloudFileEntity.builder()
                .fileName(fileName)
                .size(multLong)
                .date(Instant.now())
                .key(UUID.randomUUID())
                .userEntity(
                        UserEntity.builder()
                                .id(jwtToken.getAuthenticatedUser().getId())
                                .build())
                .build();

        var cloudId = cloudRepository.save(cloudFileEntity).getId();
        if (cloudRepository.findById(cloudId).isPresent()) {
            log.info("Файл {} записан в БД под id '{}'", fileName, cloudId);
        }
        if (!cloudManager.upload(byteArr,
                cloudFileEntity.getKey().toString(),
                cloudFileEntity.getFileName())) {
            fileNotFound("Не получилось записать файл");
        }
        log.info("Файл записан на сервер");
        return true;
    }

    @SneakyThrows
    @Transactional()
    public boolean deleteFile(String filename) {
        Optional<CloudFileEntity> foundFile = getCloudFileEntity(filename);
        if (foundFile.isEmpty()) {
            String msg = String.format("Файл %s не существует или у вас нет права доступа", filename);
            log.info(msg);
            throw new FileNotFoundException(msg);
        }
        int idFoundFile = foundFile.get().getId();
        cloudRepository.deleteById(idFoundFile);
        log.info("Произвели удаление из БД файла:  {}", filename);
        if (cloudRepository.findById(idFoundFile).isPresent()) {
            fileAlreadyExists("Файл не удалось удалить из БД");
        }
        if (!cloudManager.delete(foundFile.get())) {
            fileAlreadyExists("Файл не удалось удалить с сервера");
        }
        return true;
    }


    @SneakyThrows
    @Transactional
    public CloudFileDto getFile(String fileName) {
        Optional<CloudFileEntity> cloudFile = getCloudFileEntity(fileName);
        if (!cloudFile.isPresent()) {
            fileNotFound("Файл не удалось найди в БД");
        }
        log.info("Файл {} найден на диске", fileName);
        var resourceFromBd = cloudFile.map(cloudManager::getFile).get();
        return CloudFileDto.builder()
                .fileName(fileName)
                .resource(resourceFromBd)
                .build();
    }

    @SneakyThrows
    @Transactional()
    public boolean putFile(String fileName, CloudFileDto cloudFileDto) {
        Optional<CloudFileEntity> cloudFile = getCloudFileEntity(fileName);
        if (cloudFile.isEmpty()) {
            fileNotFound("Файл не удалось найти в БД");
        }
        if (getCloudFileEntity(cloudFileDto.getFileName()).isPresent()) {
            fileAlreadyExists("Такой файл существует");
        }
        cloudRepository.findByIdAndRenameFileName(cloudFile.get().getId(), cloudFileDto.getFileName());
            if (getCloudFileEntity(cloudFileDto.getFileName()).isEmpty()) {
            fileNotFound("Не удалось переименовать файл в БД");
        }
        if (!cloudManager.renameFileTo(cloudFile.get(), cloudFileDto.getFileName())) {
            fileNotFound("Не удалось переименовать файл на сервере");
        }
        return true;
    }

    public List<CloudFileDto> getAllFile() {
        var cloudFileEntityList = cloudRepository.findAll();
        return cloudFileEntityList.stream()
                .map(file -> CloudFileDto.builder()
                        .fileName(file.getFileName())
                        .key(file.getKey())
                        .date(file.getDate())
                        .size(file.getSize())
                        .build())
                .collect(Collectors.toList());
    }

    private Optional<CloudFileEntity> getCloudFileEntity(String fileName) {
        int userId = jwtToken.getAuthenticatedUser().getId();
        log.info("Получаем ID пользователя по токену: {}", userId);
        log.info("Начинаем искать файл в БД: {}", fileName);
        return cloudRepository.findCloudFileEntityByFileName(fileName);
    }

    private void fileNotFound(String msg) throws FileNotFoundException {
        log.error(msg);
        throw new FileNotFoundException(msg);
    }

    private void fileAlreadyExists(String msg) throws FileAlreadyExistsException {
        log.error(msg);
        throw new FileAlreadyExistsException(msg);
    }
}
