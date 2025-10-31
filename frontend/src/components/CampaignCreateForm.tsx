import React from 'react';
import { useForm } from 'react-hook-form';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createCampaign } from '../api';
import { CampaignCreateRequest } from '../types';
import { useCampaignStore } from '../stores';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Textarea } from './ui/textarea';
import toast from 'react-hot-toast';

interface CampaignCreateFormProps {
  onClose: () => void;
}

/**
 * Form for creating a new campaign.
 */
const CampaignCreateForm: React.FC<CampaignCreateFormProps> = ({ onClose }) => {
  const queryClient = useQueryClient();
  const { selectCampaign } = useCampaignStore();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<CampaignCreateRequest>();

  const createMutation = useMutation({
    mutationFn: createCampaign,
    onSuccess: (data) => {
      toast.success('Campaign created successfully!');
      queryClient.invalidateQueries({ queryKey: ['campaigns'] });
      selectCampaign(data);
      onClose();
    },
    onError: (error) => {
      toast.error('Failed to create campaign');
      console.error(error);
    },
  });

  const onSubmit = (data: CampaignCreateRequest) => {
    createMutation.mutate(data);
  };

  return (
    <div className="mt-3 space-y-3">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-3">
        <div>
          <Input
            placeholder="Campaign name"
            {...register('name', {
              required: 'Name is required',
              minLength: { value: 1, message: 'Name is required' },
              maxLength: { value: 200, message: 'Name too long' },
            })}
          />
          {errors.name && (
            <p className="text-xs text-destructive mt-1">{errors.name.message}</p>
          )}
        </div>

        <div>
          <Textarea
            placeholder="Description (optional)"
            rows={3}
            {...register('description', {
              maxLength: { value: 1000, message: 'Description too long' },
            })}
          />
          {errors.description && (
            <p className="text-xs text-destructive mt-1">
              {errors.description.message}
            </p>
          )}
        </div>

        <div className="flex gap-2">
          <Button
            type="submit"
            size="sm"
            disabled={createMutation.isPending}
            className="flex-1"
          >
            {createMutation.isPending ? 'Creating...' : 'Create'}
          </Button>
          <Button
            type="button"
            size="sm"
            variant="ghost"
            onClick={onClose}
            disabled={createMutation.isPending}
          >
            Cancel
          </Button>
        </div>
      </form>
    </div>
  );
};

export default CampaignCreateForm;

