import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { ModelingSubmission } from './modeling-submission.model';
import { createRequestOption } from '../../shared';
import { Result } from '../result';

export type EntityResponseType = HttpResponse<ModelingSubmission>;
export type ResultResponseType = HttpResponse<Result>;

@Injectable()
export class ModelingSubmissionService {

    private resourceUrl =  SERVER_API_URL + 'api/modeling-submissions';

    constructor(private http: HttpClient) { }

    create(modelingSubmission: ModelingSubmission, courseId?: number, exerciseId?: number): Observable<EntityResponseType> {
        const copy = this.convert(modelingSubmission);
        if (courseId && exerciseId) {
            return this.http.post<ModelingSubmission>(`api/courses/${courseId}/exercises/${exerciseId}/modeling-submissions`, copy, { observe: 'response' })
                .map((res: EntityResponseType) => this.convertResponse(res));
        }
        return this.http.post<ModelingSubmission>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(modelingSubmission: ModelingSubmission, courseId?: number, exerciseId?: number): Observable<EntityResponseType> {
        const copy = this.convert(modelingSubmission);
        if (courseId && exerciseId) {
            return this.http.put<ModelingSubmission>(`api/courses/${courseId}/exercises/${exerciseId}/modeling-submissions`, copy, { observe: 'response' })
                .map((res: EntityResponseType) => this.convertResponse(res));
        }
        return this.http.put<ModelingSubmission>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<ModelingSubmission>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<ModelingSubmission[]>> {
        const options = createRequestOption(req);
        return this.http.get<ModelingSubmission[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<ModelingSubmission[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    findWithModel(courseId: number, exerciseId: number, id: number): Observable<EntityResponseType> {
        return this.http.get<ModelingSubmission>(`api/courses/${courseId}/exercises/${exerciseId}/modeling-submissions/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    findByParticipation(participationId: number): Observable<EntityResponseType> {
        return this.http.get<ModelingSubmission>(`${this.resourceUrl}/participation/${participationId}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: ModelingSubmission = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<ModelingSubmission[]>): HttpResponse<ModelingSubmission[]> {
        const jsonResponse: ModelingSubmission[] = res.body;
        const body: ModelingSubmission[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to ModelingSubmission.
     */
    private convertItemFromServer(modelingSubmission: ModelingSubmission): ModelingSubmission {
        const copy: ModelingSubmission = Object.assign({}, modelingSubmission);
        return copy;
    }

    /**
     * Convert a ModelingSubmission to a JSON which can be sent to the server.
     */
    private convert(modelingSubmission: ModelingSubmission): ModelingSubmission {
        const copy: ModelingSubmission = Object.assign({}, modelingSubmission);
        return copy;
    }
}