import {
  Component, OnInit, OnDestroy, Output, EventEmitter, ViewChild, ElementRef,
  ChangeDetectorRef
} from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FaceService } from '../../../core/services/face.service';

type State = 'loading-models' | 'ready' | 'capturing' | 'processing' | 'success' | 'error';

@Component({
  selector: 'app-face-enroll',
  templateUrl: './face-enroll.component.html',
  styleUrls: ['./face-enroll.component.scss']
})
export class FaceEnrollComponent implements OnInit, OnDestroy {

  @Output() enrolled = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();
  @ViewChild('videoEl') videoRef!: ElementRef<HTMLVideoElement>;
  @ViewChild('canvasEl') canvasRef!: ElementRef<HTMLCanvasElement>;

  state: State = 'loading-models';
  errorMsg = '';
  faceDetected = false;

  private stream: MediaStream | null = null;
  private detectionLoop: any = null;

  constructor(
    private faceService: FaceService,
    private snack: MatSnackBar,
    private cdr: ChangeDetectorRef
  ) {}

  async ngOnInit(): Promise<void> {
    try {
      await this.faceService.loadModels();
      this.state = 'ready';
    } catch {
      this.state = 'error';
      this.errorMsg = 'Impossible de charger les modèles. Vérifiez que les fichiers sont dans src/assets/models/';
    }
  }

  async startCamera(): Promise<void> {
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' } });

      // Set state FIRST so Angular renders the <video> element in the DOM
      this.state = 'capturing';
      this.cdr.detectChanges(); // force synchronous change detection
      await new Promise<void>(r => setTimeout(r, 50)); // wait one tick for DOM update

      const video = this.videoRef.nativeElement;
      video.srcObject = this.stream;
      await video.play();
      this.runDetectionLoop();
    } catch (err: any) {
      console.error('Camera error:', err);
      this.state = 'error';
      if (err?.name === 'NotAllowedError' || err?.name === 'PermissionDeniedError') {
        this.errorMsg = 'Permission caméra refusée. Autorisez la caméra dans la barre d\'adresse du navigateur.';
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

  async captureAndEnroll(): Promise<void> {
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

      this.faceService.enrollFace(descriptor).subscribe({
        next: () => {
          this.stopCamera();
          this.state = 'success';
          this.enrolled.emit();
        },
        error: () => {
          this.state = 'error';
          this.errorMsg = 'Erreur lors de l\'enregistrement côté serveur.';
        }
      });
    } catch {
      this.state = 'error';
      this.errorMsg = 'Erreur lors de la capture du visage.';
    }
  }

  cancel(): void {
    this.stopCamera();
    this.cancelled.emit();
  }

  retry(): void {
    this.errorMsg = '';
    this.state = 'ready';
  }

  private stopCamera(): void {
    clearTimeout(this.detectionLoop);
    this.stream?.getTracks().forEach(t => t.stop());
    this.stream = null;
  }

  ngOnDestroy(): void {
    this.stopCamera();
  }
}
