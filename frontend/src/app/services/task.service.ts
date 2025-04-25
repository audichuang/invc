import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject, merge } from 'rxjs';
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
    receivedAt?: Date;
    system?: 'fund' | 'bond'; // 添加系統標識
}

@Injectable({
    providedIn: 'root'
})
export class TaskService {
    private fundApiUrl = 'http://localhost:8080/api'; // 基金系統API
    private bondApiUrl = 'http://localhost:8081/api'; // 債券系統API - 使用新的反向代理
    private eventSubject = new Subject<TaskEvent>();
    public events$ = this.eventSubject.asObservable();
    private eventSources: Map<string, EventSource> = new Map(); // 存儲多個SSE連接
    private lastHeartbeatTime: Map<string, Date> = new Map(); // 存儲每個連接的最後心跳時間

    constructor(private http: HttpClient) { }

    /**
     * 產生相關ID
     */
    generateCorrelationId(): string {
        return uuidv4();
    }

    /**
     * 發起任務請求 (已被FundBondService取代，保留向後兼容)
     */
    initiateTask(request: TaskRequest): Observable<string> {
        return this.http.post<string>(`${this.fundApiUrl}/first-api`, request, {
            responseType: 'text' as 'json'
        });
    }

    /**
     * 建立 SSE 連接
     * @param correlationId 關聯ID
     * @param system 系統類型 ('fund' 或 'bond')
     */
    connectToEventStream(correlationId: string, system: 'fund' | 'bond' = 'fund'): void {
        // 檢查是否已有此連接
        if (this.eventSources.has(correlationId)) {
            console.log(`已存在的SSE連接 (${correlationId})`);
            return;
        }

        // 確定正確的API URL
        const apiUrl = system === 'fund' ? this.fundApiUrl : this.bondApiUrl;

        // 使用正確的事件端點
        const eventsEndpoint = system === 'fund' ? 'fund-events' : 'bond-events';

        // 建立新的 SSE 連接
        const eventSource = new EventSource(`${apiUrl}/${eventsEndpoint}/${correlationId}`);
        this.eventSources.set(correlationId, eventSource);
        this.lastHeartbeatTime.set(correlationId, new Date());

        // Helper function to process and add timestamp
        const processEvent = (eventData: string, eventName?: string) => {
            try {
                const taskEvent: TaskEvent = JSON.parse(eventData);
                taskEvent.receivedAt = new Date();
                taskEvent.system = system; // 添加系統標識

                // 特殊處理心跳事件
                if (eventName === 'HEARTBEAT') {
                    this.lastHeartbeatTime.set(correlationId, new Date());
                    console.log(`收到 ${system} 系統心跳事件:`, taskEvent);
                    return; // 不轉發心跳事件給其他訂閱者
                }

                console.log(`收到 ${system} 系統 SSE 事件 (${eventName || 'message'}):`, taskEvent);
                this.eventSubject.next(taskEvent);

                if (taskEvent.finalEvent) {
                    this.disconnectEventStream(correlationId);
                }
            } catch (error) {
                console.error(`解析 ${system} 系統 SSE 事件 (${eventName || 'message'}) 失敗:`, eventData, error);
                this.eventSubject.next({
                    correlationId,
                    status: 'ERROR',
                    message: `客戶端解析事件失敗: ${eventData}`,
                    finalEvent: true,
                    receivedAt: new Date(),
                    system
                });
                this.disconnectEventStream(correlationId);
            }
        };

        eventSource.onmessage = (event) => {
            processEvent(event.data);
        };

        // Use helper for specific listeners
        eventSource.addEventListener('CONNECTED', (event: any) => processEvent(event.data, 'CONNECTED'));
        eventSource.addEventListener('PROCESSING', (event: any) => processEvent(event.data, 'PROCESSING'));
        eventSource.addEventListener('SUBTASK_COMPLETED', (event: any) => processEvent(event.data, 'SUBTASK_COMPLETED'));
        eventSource.addEventListener('COMPLETED', (event: any) => processEvent(event.data, 'COMPLETED'));
        eventSource.addEventListener('FAILED', (event: any) => processEvent(event.data, 'FAILED'));
        eventSource.addEventListener('HEARTBEAT', (event: any) => processEvent(event.data, 'HEARTBEAT'));
        eventSource.addEventListener('BATCH_COMPLETED', (event: any) => processEvent(event.data, 'BATCH_COMPLETED'));

        eventSource.onerror = (error) => {
            console.error(`${system} 系統 SSE 連接錯誤:`, error);
            this.disconnectEventStream(correlationId);
            this.eventSubject.next({
                correlationId,
                status: 'ERROR',
                message: `${system} 系統 SSE 連接出錯`,
                finalEvent: true,
                receivedAt: new Date(),
                system
            });
        };

        console.log(`${system} 系統 SSE 連接已建立 (${correlationId})`);
    }

    /**
     * 檢查連接健康狀態
     * @param correlationId 關聯ID
     * @returns 如果最後一次心跳在30秒內，返回true
     */
    isConnectionHealthy(correlationId: string): boolean {
        const lastHeartbeat = this.lastHeartbeatTime.get(correlationId);
        if (!lastHeartbeat) return false;

        const now = new Date();
        const diff = now.getTime() - lastHeartbeat.getTime();
        return diff < 30000; // 30秒內有心跳
    }

    /**
     * 取得最後一次心跳時間
     */
    getLastHeartbeatTime(correlationId: string): Date | undefined {
        return this.lastHeartbeatTime.get(correlationId);
    }

    /**
     * 關閉特定 SSE 連接
     */
    disconnectEventStream(correlationId: string): void {
        const eventSource = this.eventSources.get(correlationId);
        if (eventSource) {
            eventSource.close();
            this.eventSources.delete(correlationId);
            this.lastHeartbeatTime.delete(correlationId);
            console.log(`SSE 連接 ${correlationId} 已關閉`);
        }
    }

    /**
     * 關閉所有 SSE 連接
     */
    disconnectAllEventStreams(): void {
        this.eventSources.forEach((eventSource, id) => {
            eventSource.close();
            console.log(`SSE 連接 ${id} 已關閉`);
        });
        this.eventSources.clear();
        this.lastHeartbeatTime.clear();
    }
} 