import {
  Component, OnInit, OnDestroy, ViewChild, ElementRef, ChangeDetectorRef
} from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { KeycloakService } from 'keycloak-angular';
import { FaceAuthService } from '../../core/services/face-auth.service';
import { FaceService } from '../../core/services/face.service';

type Mode = 'choose' | 'face-setup' | 'face-capture' | 'face-processing';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit, OnDestroy {

  @ViewChild('videoEl') videoRef!: ElementRef<HTMLVideoElement>;
  @ViewChild('canvasEl') canvasRef!: ElementRef<HTMLCanvasElement>;

  mode: Mode = 'choose';
  faceForm!: FormGroup;
  faceDetected = false;
  autoLoginProgress = 0;   // 0-100 fill shown in the progress ring
  errorMsg = '';
  modelsLoaded = false;

  private stream: MediaStream | null = null;
  private detectionLoop: any = null;
  private stableTimer: any = null;
  private stableStart: number | null = null;
  private readonly STABLE_MS = 1500;  // auto-login after 1.5 s continuous detection

  constructor(
    private keycloak: KeycloakService,
    private faceAuth: FaceAuthService,
    private faceService: FaceService,
    private fb: FormBuilder,
    private snack: MatSnackBar,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    // Valid session → go home immediately (no interaction needed)
    if (this.keycloak.isLoggedIn() || this.faceAuth.isAuthenticated()) {
      this.router.navigate(['/']);
      return;
    }

    // Expired face token → attempt silent refresh before showing the login form
    if (this.faceAuth.hasExpiredToken()) {
      this.mode = 'face-processing';
      this.faceAuth.refreshToken().subscribe({
        next: res => {
          this.faceAuth.saveSession(res);
          this.router.navigate(['/']);
        },
        error: () => {
          // Refresh rejected (grace period exceeded or enrollment revoked) → clean start
          this.faceAuth.clearToken();
          this.mode = 'choose';
        }
      });
      this.faceForm = this.fb.group({ username: ['', Validators.required] });
      this.faceService.loadModels().then(() => this.modelsLoaded = true).catch(() => {});
      return;
    }

    this.faceForm = this.fb.group({ username: ['', Validators.required] });
    this.faceService.loadModels().then(() => this.modelsLoaded = true).catch(() => {});
  }

  loginWithPassword(): void {
    this.keycloak.login({ redirectUri: window.location.origin });
  }

  goToFaceLogin(): void {
    this.mode = 'face-setup';
    this.errorMsg = '';
  }

  async startFaceCamera(): Promise<void> {
    if (this.faceForm.invalid) {
      this.faceForm.markAllAsTouched();
      return;
    }
    this.errorMsg = '';
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' } });
      this.mode = 'face-capture';
      this.cdr.detectChanges();
      await new Promise<void>(r => setTimeout(r, 50));

      const video = this.videoRef.nativeElement;
      video.srcObject = this.stream;
      await video.play();
      this.runDetectionLoop();
    } catch (err: any) {
      this.errorMsg = err?.name === 'NotAllowedError'
        ? 'Permission caméra refusée.'
        : 'Impossible d\'accéder à la caméra.';
    }
  }

  private runDetectionLoop(): void {
    const faceapi = this.faceService.getFaceapi();
    if (!faceapi) return;

    const tick = async () => {
      if (this.mode !== 'face-capture') return;
      const video = this.videoRef?.nativeElement;
      const canvas = this.canvasRef?.nativeElement;
      if (!video || !canvas || video.videoWidth === 0) {
        this.detectionLoop = setTimeout(tick, 200);
        return;
      }
      const displaySize = { width: video.videoWidth, height: video.videoHeight };
      faceapi.matchDimensions(canvas, displaySize);
      const detections = await faceapi
        .detectAllFaces(video, new faceapi.TinyFaceDetectorOptions())
        .withFaceLandmarks(true);
      const resized = faceapi.resizeResults(detections, displaySize);
      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        faceapi.draw.drawDetections(canvas, resized);
        faceapi.draw.drawFaceLandmarks(canvas, resized);
      }
      const faceNow = detections.length > 0;

      if (faceNow) {
        if (!this.stableStart) this.stableStart = Date.now();
        const elapsed = Date.now() - this.stableStart;
        this.autoLoginProgress = Math.min(100, (elapsed / this.STABLE_MS) * 100);
        if (elapsed >= this.STABLE_MS && this.mode === 'face-capture') {
          this.faceDetected = true;
          this.cdr.detectChanges();
          this.verifyAndLogin();
          return;
        }
      } else {
        this.stableStart = null;
        this.autoLoginProgress = 0;
      }

      this.faceDetected = faceNow;
      this.cdr.detectChanges();
      this.detectionLoop = setTimeout(tick, 100);
    };
    tick();
  }

  async verifyAndLogin(): Promise<void> {
    if (this.mode !== 'face-capture') return;
    clearTimeout(this.detectionLoop);
    this.mode = 'face-processing';

    try {
      const descriptor = await this.faceService.detectDescriptor(this.videoRef.nativeElement);
      if (!descriptor) {
        this.mode = 'face-capture';
        this.snack.open('Aucun visage détecté — réessayez', 'OK', { duration: 3000 });
        this.runDetectionLoop();
        return;
      }

      const username = this.faceForm.value.username;
      this.faceAuth.faceLogin(username, descriptor).subscribe({
        next: res => {
          this.stopCamera();
          this.faceAuth.saveSession(res);
          this.router.navigate(['/']);
        },
        error: err => {
          this.mode = 'face-capture';
          this.errorMsg = err?.error?.message ?? 'Visage non reconnu. Réessayez.';
          this.runDetectionLoop();
        }
      });
    } catch {
      this.mode = 'face-capture';
      this.errorMsg = 'Erreur lors de la capture. Réessayez.';
      this.runDetectionLoop();
    }
  }

  back(): void {
    this.stopCamera();
    this.mode = 'choose';
    this.errorMsg = '';
    this.faceDetected = false;
  }

  private stopCamera(): void {
    clearTimeout(this.detectionLoop);
    this.stableStart = null;
    this.autoLoginProgress = 0;
    this.stream?.getTracks().forEach(t => t.stop());
    this.stream = null;
  }

  ngOnDestroy(): void { this.stopCamera(); }
}
