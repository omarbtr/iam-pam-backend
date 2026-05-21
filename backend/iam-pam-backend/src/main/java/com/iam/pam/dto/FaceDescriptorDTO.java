package com.iam.pam.dto;

import java.time.LocalDateTime;

public class FaceDescriptorDTO {

    public static class EnrollRequest {
        private float[] descriptor;
        public float[] getDescriptor() { return descriptor; }
        public void setDescriptor(float[] descriptor) { this.descriptor = descriptor; }
    }

    public static class VerifyRequest {
        private float[] descriptor;
        public float[] getDescriptor() { return descriptor; }
        public void setDescriptor(float[] descriptor) { this.descriptor = descriptor; }
    }

    public static class VerifyResponse {
        private boolean match;
        private double distance;

        public boolean isMatch() { return match; }
        public void setMatch(boolean match) { this.match = match; }
        public double getDistance() { return distance; }
        public void setDistance(double distance) { this.distance = distance; }
    }

    public static class StatusResponse {
        private boolean enrolled;
        private LocalDateTime enrolledAt;

        public boolean isEnrolled() { return enrolled; }
        public void setEnrolled(boolean enrolled) { this.enrolled = enrolled; }
        public LocalDateTime getEnrolledAt() { return enrolledAt; }
        public void setEnrolledAt(LocalDateTime enrolledAt) { this.enrolledAt = enrolledAt; }
    }
}
