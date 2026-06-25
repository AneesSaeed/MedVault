import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PendingMedicalFilesComponent } from './pending-medical-files.component';

describe('PendingMedicalFilesComponent', () => {
  let component: PendingMedicalFilesComponent;
  let fixture: ComponentFixture<PendingMedicalFilesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ PendingMedicalFilesComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PendingMedicalFilesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
