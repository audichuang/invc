import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { v4 as uuidv4 } from 'uuid';

export interface TaskRequest {
    correlationId: string;
    taskName: string;
    numberOfSubtasks: number;
}

export interface TaskEvent {
    correlationId: string;
    status: string;
    message: string;
    result?: any;
    finalEvent: boolean;
}

@Injectable({
    providedIn: 'root'
})
export class TaskService {
    private apiUrl = 'http://localhost:8080/api';
    private eventSubject = new Subject<TaskEvent>();
    public events$ = this.eventSubject.asObservable();
    private eventSource: EventSource | null = null;

    constructor(private http: HttpClient) { }

    /**
     * 產生相關ID
     */
    generateCorrelationId(): string {
        return uuidv4();
    }

    /**
     * 發起任務請求
     */
    initiateTask(request: TaskRequest): Observable<string> {
        return this.http.post<string>(`${this.apiUrl}/first-api`, request, {
            responseType: 'text' as 'json'
        });
    }

    /**
     * 建立 SSE 連接
     */
    connectToEventStream(correlationId: string): void {
        // 關閉現有連接（如果有）
        this.disconnectEventStream();

        // 建立新的 SSE 連接
        this.eventSource = new EventSource(`${this.apiUrl}/events/${correlationId}`);

        // 處理收到的事件
        this.eventSource.onmessage = (event) => {
            console.log('收到 SSE 事件:', event.data);
            const taskEvent: TaskEvent = JSON.parse(event.data);
            this.eventSubject.next(taskEvent);

            // 如果是最終事件，關閉連接
            if (taskEvent.finalEvent) {
                this.disconnectEventStream();
            }
        };

        // 處理 SSE 連接的各種事件
        this.eventSource.addEventListener('CONNECTED', (event: any) => {
            const taskEvent: TaskEvent = JSON.parse(event.data);
            this.eventSubject.next(taskEvent);
        });

        this.eventSource.addEventListener('PROCESSING', (event: any) => {
            const taskEvent: TaskEvent = JSON.parse(event.data);
            this.eventSubject.next(taskEvent);
        });

        this.eventSource.addEventListener('SUBTASK_COMPLETED', (event: any) => {
            const taskEvent: TaskEvent = JSON.parse(event.data);
            this.eventSubject.next(taskEvent);
        });

        this.eventSource.addEventListener('COMPLETED', (event: any) => {
            const taskEvent: TaskEvent = JSON.parse(event.data);
            this.eventSubject.next(taskEvent);
            this.disconnectEventStream();
        });

        this.eventSource.addEventListener('FAILED', (event: any) => {
            const taskEvent: TaskEvent = JSON.parse(event.data);
            this.eventSubject.next(taskEvent);
            this.disconnectEventStream();
        });

        this.eventSource.onerror = (error) => {
            console.error('SSE 連接錯誤:', error);
            this.disconnectEventStream();
            this.eventSubject.next({
                correlationId,
                status: 'ERROR',
                message: 'SSE 連接出錯',
                finalEvent: true
            });
        };
    }

    /**
     * 關閉 SSE 連接
     */
    disconnectEventStream(): void {
        if (this.eventSource) {
            this.eventSource.close();
            this.eventSource = null;
        }
    }
} 