export class ModalRef<T = any> {

  private _closed = false;

  constructor(private closeFn: (result?: T) => void) {}

  close(result?: T) {
    if (this._closed) return;
    this._closed = true;
    this.closeFn(result);
  }
}
