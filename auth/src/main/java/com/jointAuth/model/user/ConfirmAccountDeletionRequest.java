package com.jointAuth.model.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfirmAccountDeletionRequest implements Serializable {
    private Long userId;
    private String verificationCode;
}
