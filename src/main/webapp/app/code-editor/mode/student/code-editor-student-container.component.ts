import { Component, OnInit } from '@angular/core';
import { tap } from 'rxjs/operators';
import { Participation, ParticipationService } from 'app/entities/participation';
import { CodeEditorContainer } from '../base/code-editor-mode-container.component';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-code-editor-student',
    templateUrl: './code-editor-student-container.component.html',
    providers: [],
})
export class CodeEditorStudentContainerComponent extends CodeEditorContainer implements OnInit {
    participation: Participation;
    constructor(participationService: ParticipationService, translateService: TranslateService, route: ActivatedRoute) {
        super(participationService, translateService, route);
    }
    ngOnInit(): void {
        this.paramSub = this.route.params.subscribe(params => {
            const participationId = Number(params['participationId']);
            this.loadParticipation(participationId)
                .pipe(tap(participation => (this.participation = participation)))
                .subscribe();
        });
    }
}