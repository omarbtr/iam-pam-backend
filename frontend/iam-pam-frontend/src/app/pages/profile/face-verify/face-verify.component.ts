import {
  Component, OnInit, OnDestroy, ViewChild, ElementRef, ChangeDetectorRef
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FaceService, FaceVerifyResponse } from '../../../core/services/face.service';
import { AuthService } from '../../../core/services/auth.service';

const FACE_VERIFIED_KEY = 'iam_face_verified';

type State = 'loading-models' | 'ready' | 'capturing' | 'processing' | 'success' | 'failed' | 'error' | 'not-enrolled';

@Component({
  selector: 'app-face-verify',
  templateUrl: './face-verify.component.html',
  styleUrls: ['./face-verify.component.scss']
})
export class FaceVerifyComponent implements OnInit, OnDestroy {

  @ViewChild('videoEl') videoRef!: ElementRef<HTMLVideoElement>;
  @ViewChild('canvasEl') canvasRef!: ElementRef<HTMLCanvasElement>;

  state: State = 'loading-models';
  errorMsg = '';
  faceDetected = false;
  verifyResult: FaceVerifyResponse | null = null;

  // fromLogin=true → page affichée après la connexion (bouton Ignorer disponible)
  fromLogin = false;
  private returnUrl = '';

  private stream: MediaStream | null = null;
  private detectionLoop: any = null;

  constructor(
    private faceService: FaceService,
    public auth: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private snack: MatSnackBar,
    private cdr: ChangeDetectorRef
  ) {}

  async ngOnInit(): Promise<void> {
    this.route.queryParams.subscribe(params => {
      this.fromLogin = params['fromLogin'] === 'true';
      this.returnUrl = params['returnUrl'] || '';
    });

    try {
      await this.faceService.loadModels();
      this.checkEnrollmentStatus();
    } catch {
      this.state = 'error';
      this.errorMsg = 'Impossible de charger les modèles. Vérifiez que face-api.js est installé.';
    }
  }

  private checkEnrollmentStatus(): void {
    this.faceService.getStatus().subscribe({
      next: s => this.state = s.enrolled ? 'ready' : 'not-enrolled',
      error: () => { this.state = 'error'; this.errorMsg = 'Erreur de connexion au serveur.'; }
    });
  }

  async startCamera(): Promise<void> {
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' } });

      this.state = 'capturing';
      this.cdr.detectChanges();
      await new Promise<void>(r => setTimeout(r, 50));

      const video = this.videoRef.nativeElement;
      video.srcObject = this.stream;
      await video.play();
      this.runDetectionLoop();
    } catch (err: any) {
      console.error('Camera error:', err);
      this.state = 'error';
      if (err?.name === 'NotAllowedError' || err?.name === 'PermissionDeniedError') {
        this.errorMsg = 'Permission caméra refusée. Cliquez sur l\'icône caméra dans la barre d\'adresse pour autoriser.';
      } else if (err?.name === 'NotFoundError') {
        this.errorMsg = 'Aucune caméra détectée sur cet appareil.';
      } else {
        this.errorMsg = 'Impossible d\'accéder à la caméra : ' + (err?.message || err?.name || 'erreur inconnue');
      }
    }
  }

  private runDetectionLoop(): void {
    const faceapi = this.faceService.getFaceapi();
    if (!faceapi) return;

    const tick = async () => {
      if (this.state !== 'capturing') return;
      const video = this.videoRef.nativeElement;
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
      this.faceDetected = detections.length > 0;
      this.detectionLoop = setTimeout(tick, 200);
    };
    tick();
  }

  async captureAndVerify(): Promise<void> {
    if (this.state !== 'capturing') return;
    clearTimeout(this.detectionLoop);
    this.state = 'processing';

    try {
      const descriptor = await this.faceService.detectDescriptor(this.videoRef.nativeElement);
      if (!descriptor) {
        this.state = 'capturing';
        this.snack.open('Aucun visage détecté — placez-vous face à la caméra', 'OK', { duration: 4000 });
        this.runDetectionLoop();
        return;
      }

      this.faceService.verifyFace(descriptor).subscribe({
        next: result => {
          this.stopCamera();
          this.verifyResult = result;
          if (result.match) {
            // Marquer la session comme vérifiée
            sessionStorage.setItem(FACE_VERIFIED_KEY, 'true');
            this.state = 'success';
          } else {
            this.state = 'failed';
          }
        },
        error: () => {
          this.state = 'error';
          this.errorMsg = 'Erreur lors de la vérification côté serveur.';
        }
      });
    } catch {
      this.state = 'error';
      this.errorMsg = 'Erreur lors de la capture du visage.';
    }
  }

  continueToApp(): void {
    sessionStorage.setItem(FACE_VERIFIED_KEY, 'true');
    this.navigateAway();
  }

  skip(): void {
    // Ignorer la vérification faciale pour cette session
    sessionStorage.setItem(FACE_VERIFIED_KEY, 'true');
    this.navigateAway();
  }

  private navigateAway(): void {
    if (this.returnUrl) {
      this.router.navigateByUrl(this.returnUrl);
    } else {
      this.goHome();
    }
  }

  goHome(): void {
    if (this.auth.isAdmin())            this.router.navigate(['/admin/dashboard']);
    else if (this.auth.isTenantAdmin()) this.router.navigate(['/tenant-admin/dashboard']);
    else if (this.auth.isAuditor())     this.router.navigate(['/auditor/logs']);
    else                                this.router.navigate(['/user/my-requests']);
  }

  goToMfa(): void { this.router.navigate(['/profile/mfa']); }

  retry(): void {
    this.verifyResult = null;
    this.state = 'ready';
  }

  private stopCamera(): void {
    clearTimeout(this.detectionLoop);
    this.stream?.getTracks().forEach(t => t.stop());
    this.stream = null;
  }

  ngOnDestroy(): void { this.stopCamera(); }
}
