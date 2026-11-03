import api from '../api/axios';

export interface CourierProfileSummary {
  vehicleType: string;
  vehicleNumber: string;
  active: boolean;
}

export interface AdminUserSummary {
  id: string;
  fullName: string;
  email: string;
  phone: string | null;
  role: 'CUSTOMER' | 'MERCHANT' | 'COURIER' | 'ADMIN';
  status: 'ACTIVE' | 'DEACTIVATED' | 'SUSPENDED' | 'PENDING_VERIFICATION';
  authProvider: string;
  emailVerified: boolean;
  mustChangePassword: boolean;
  createdAt: string;
  lastLoginAt: string | null;
  courierProfile: CourierProfileSummary | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface ListUsersParams {
  page?: number;
  size?: number;
  role?: string;
  status?: string;
  search?: string;
}

export interface CreateUserPayload {
  email: string;
  fullName: string;
  phone?: string;
  role: 'ADMIN' | 'COURIER';
  vehicleType?: string;
  vehicleNumber?: string;
}

export interface CreateUserResponse {
  user: AdminUserSummary;
  temporaryPassword: string;
}

export interface EditUserPayload {
  fullName?: string;
  phone?: string;
  vehicleType?: string;
  vehicleNumber?: string;
}

export interface ResetPasswordResponse {
  temporaryPassword: string;
  message: string;
}

export const adminService = {
  async listUsers(params?: ListUsersParams): Promise<PageResponse<AdminUserSummary>> {
    const response = await api.get<PageResponse<AdminUserSummary>>('/admin/users', {
      params: {
        page: params?.page ?? 0,
        size: params?.size ?? 10,
        role: params?.role || undefined,
        status: params?.status || undefined,
        search: params?.search ? params.search.trim() : undefined,
      },
    });
    return response.data;
  },

  async getUser(id: string): Promise<AdminUserSummary> {
    const response = await api.get<AdminUserSummary>(`/admin/users/${id}`);
    return response.data;
  },

  async createUser(payload: CreateUserPayload): Promise<CreateUserResponse> {
    const response = await api.post<CreateUserResponse>('/admin/users', payload);
    return response.data;
  },

  async editUser(id: string, payload: EditUserPayload): Promise<AdminUserSummary> {
    const response = await api.patch<AdminUserSummary>(`/admin/users/${id}`, payload);
    return response.data;
  },

  async activateUser(id: string): Promise<AdminUserSummary> {
    const response = await api.post<AdminUserSummary>(`/admin/users/${id}/activate`);
    return response.data;
  },

  async deactivateUser(id: string): Promise<AdminUserSummary> {
    const response = await api.post<AdminUserSummary>(`/admin/users/${id}/deactivate`);
    return response.data;
  },

  async resetPassword(id: string): Promise<ResetPasswordResponse> {
    const response = await api.post<ResetPasswordResponse>(`/admin/users/${id}/reset-password`);
    return response.data;
  },
};

