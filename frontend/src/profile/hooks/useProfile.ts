import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { changePassword, deactivateAccount, getProfile, updateProfile } from '../api/ProfileApi'

export function useProfile() {
  return useQuery({
    queryKey: ['profile'],
    queryFn: getProfile,
  })
}

export function useUpdateProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (patch: { name?: string; email?: string }) => updateProfile(patch),
    onSuccess: (profile) => {
      queryClient.setQueryData(['profile'], profile)
      queryClient.invalidateQueries({ queryKey: ['metrics'] })
    },
  })
}

export function useChangePassword() {
  return useMutation({
    mutationFn: ({ currentPassword, newPassword }: { currentPassword: string; newPassword: string }) =>
      changePassword(currentPassword, newPassword),
  })
}

export function useDeactivate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: deactivateAccount,
    onSuccess: () => queryClient.clear(),
  })
}
