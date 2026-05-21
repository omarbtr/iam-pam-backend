import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';

export interface FaceStatusResponse { enrolled: boolean; enrolledAt?: string; }
export interface FaceVerifyResponse  { match: boolean; distance: number; }

// Modèles chargés depuis jsDelivr CDN (requiert internet)
// Pour usage offline : télécharger les .json + shards dans src/assets/models/ et mettre '/assets/models'
const MODEL_URL = 'https://cdn.jsdelivr.net/gh/justadudewhohacks/face-api.js@0.22.2/weights';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
declare const require: any;

@Injectable({ providedIn: 'root' })
export class FaceService {

  private url = `${environment.apiUrl}/user/face`;
  private modelsLoaded = false;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private faceapi: any = null;

  constructor(private http: HttpClient) {}

  async loadModels(): Promise<void> {
    if (this.modelsLoaded) return;
    this.faceapi = require('face-api.js');
    await Promise.all([
      this.faceapi.nets.tinyFaceDetector.loadFromUri(MODEL_URL),
      this.faceapi.nets.faceLandmark68TinyNet.loadFromUri(MODEL_URL),
      this.faceapi.nets.faceRecognitionNet.loadFromUri(MODEL_URL)
    ]);
    this.modelsLoaded = true;
  }

  getFaceapi(): any { return this.faceapi; }

  async detectDescriptor(videoEl: HTMLVideoElement): Promise<Float32Array | null> {
    if (!this.faceapi) return null;
    const result = await this.faceapi
      .detectSingleFace(videoEl, new this.faceapi.TinyFaceDetectorOptions())
      .withFaceLandmarks(true)
      .withFaceDescriptor();
    return result?.descriptor ?? null;
  }

  enrollFace(descriptor: Float32Array): Observable<void> {
    return this.http.post<ApiResponse<void>>(`${this.url}/enroll`, { descriptor: Array.from(descriptor) })
      .pipe(map(() => void 0));
  }

  verifyFace(descriptor: Float32Array): Observable<FaceVerifyResponse> {
    return this.http.post<ApiResponse<FaceVerifyResponse>>(`${this.url}/verify`, { descriptor: Array.from(descriptor) })
      .pipe(map(r => r.data));
  }

  getStatus(): Observable<FaceStatusResponse> {
    return this.http.get<ApiResponse<FaceStatusResponse>>(`${this.url}/status`)
      .pipe(map(r => r.data));
  }

  removeEnrollment(): Observable<void> {
    return this.http.delete<ApiResponse<void>>(`${this.url}/enroll`)
      .pipe(map(() => void 0));
  }
}
