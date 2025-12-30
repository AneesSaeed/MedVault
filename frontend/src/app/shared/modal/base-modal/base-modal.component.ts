import { CommonModule } from '@angular/common';
import {
  Component,
  EventEmitter,
  HostListener,
  Injector,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  Type,
  inject
} from '@angular/core';
import { MODAL_DATA } from 'src/app/shared/modal/base-modal/modal.tokens';
import { ModalRef } from './modal-ref';

@Component({
  selector: 'app-base-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './base-modal.component.html',
  styleUrls: ['./base-modal.component.scss']
})
export class BaseModalComponent implements OnChanges {
  @Input() title = '';
  @Input() component!: Type<unknown>;
  @Input() data: unknown;

  @Output() closed = new EventEmitter<unknown>();

  childInjector!: Injector;
  private modalRef!: ModalRef;
  private readonly injector = inject(Injector);

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['component'] || changes['data']) {
      this.modalRef = new ModalRef((result) => this.closed.emit(result));

      this.childInjector = Injector.create({
        parent: this.injector,
        providers: [
          { provide: MODAL_DATA, useValue: this.data },
          { provide: ModalRef, useValue: this.modalRef }
        ]
      });
    }
  }

  close() {
    this.modalRef?.close();
  }

  @HostListener('document:keydown.escape')
  onEsc() {
    this.close();
  }
}
