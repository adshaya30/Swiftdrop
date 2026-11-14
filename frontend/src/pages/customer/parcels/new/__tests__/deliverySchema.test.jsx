import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useForm } from 'react-hook-form';
import { createDeliverySchema, createDeliveryResolver } from '../deliverySchema';

const references = (maxWeightKg = '17.25') => ({
  maxWeightKg, districts: [{ code: 'COLOMBO' }, { code: 'KANDY' }],
  sizes: [{ size: 'SMALL' }, { size: 'MEDIUM' }],
});
const validInput = () => {
  const address = { contactName: 'Test Customer', contactPhone: '077 123 4567', addressLine1: '12 Test Road', city: 'Colombo', districtCode: 'COLOMBO' };
  return { pickup: { ...address }, dropoff: { ...address, districtCode: 'KANDY' }, parcel: { weightKg: '1.20', size: 'SMALL' } };
};
const setField = (body, path, value) => {
  const keys = path.split('.');
  const key = keys.pop();
  keys.reduce((object, part) => object[part], body)[key] = value;
  return body;
};

describe('TST-040 customer delivery validation boundaries', () => {
  it('preserves decimal text and normalizes phones without adding fees', () => {
    const result = createDeliverySchema(references()).parse(validInput());
    expect(result.parcel.weightKg).toBe('1.20');
    expect(result.pickup.contactPhone).toBe('+94771234567');
    expect(result).not.toHaveProperty('expectedFee');
    expect(result).not.toHaveProperty('fee');
  });

  it.each(['0.01', '1', '1.00', '17.25', 1.2])('accepts valid weight %s', (weight) => {
    expect(createDeliverySchema(references()).safeParse(setField(validInput(), 'parcel.weightKg', weight)).success).toBe(true);
  });
  it.each(['', ' ', '0', '0.00', '-1', '0.001', '1.200', '17.26', '1e1', '1,20', 'NaN', Infinity, NaN, null, undefined])('rejects invalid weight %s', (weight) => {
    const result = createDeliverySchema(references()).safeParse(setField(validInput(), 'parcel.weightKg', weight));
    expect(result.success).toBe(false);
    expect(result.error.issues.some(({ path }) => path.join('.') === 'parcel.weightKg')).toBe(true);
  });

  it('uses changed server maximums, including limits above the usual maximum', () => {
    const body = setField(validInput(), 'parcel.weightKg', '35.00');
    expect(createDeliverySchema(references('40.00')).safeParse(body).success).toBe(true);
    expect(createDeliverySchema(references('34.99')).safeParse(body).success).toBe(false);
    expect(createDeliverySchema(references(35)).safeParse(body).success).toBe(true);
  });
  it.each([undefined, {}, references('bad'), references('0.00'), { ...references(), sizes: [] }, { ...references(), districts: [] }])('requires usable backend reference data', (data) => {
    expect(() => createDeliverySchema(data)).toThrow('reference data');
  });

  it.each(['pickup', 'dropoff'])('checks %s name, address, city, district and postal boundaries', (prefix) => {
    const schema = createDeliverySchema(references());
    const cases = [
      ['contactName', 'AB', true], ['contactName', 'A', false], ['contactName', 'A'.repeat(100), true], ['contactName', 'A'.repeat(101), false],
      ['contactName', '  ', false], ['contactPhone', undefined, false], ['addressLine1', 'X', true], ['addressLine1', '', false], ['addressLine1', '  ', false],
      ['addressLine1', 'A'.repeat(150), true], ['addressLine1', 'A'.repeat(151), false],
      ['addressLine2', 'A'.repeat(150), true], ['addressLine2', 'A'.repeat(151), false],
      ['city', 'A'.repeat(80), true], ['city', 'A'.repeat(81), false], ['city', '', false],
      ['districtCode', '', false], ['districtCode', 'UNKNOWN', false],
      ['postalCode', '00100', true], ['postalCode', '1234', false], ['postalCode', '123456', false], ['postalCode', '12A45', false],
    ];
    for (const [field, value, success] of cases) {
      expect(schema.safeParse(setField(validInput(), `${prefix}.${field}`, value)).success, `${prefix}.${field}: ${value}`).toBe(success);
    }
  });

  it.each(['0771234567', '+94771234567', '94771234567', '(011) 234-5678', '+94 (77) 123 4567'])('accepts backend-supported phone %s', (value) => {
    expect(createDeliverySchema(references()).safeParse(setField(validInput(), 'dropoff.contactPhone', value)).success).toBe(true);
  });
  it.each(['', '123', '+447712345678', '07712345678', '+94071234567', 'phone', '077/1234567'])('rejects phone %s for both contacts', (value) => {
    for (const prefix of ['pickup', 'dropoff']) {
      expect(createDeliverySchema(references()).safeParse(setField(validInput(), `${prefix}.contactPhone`, value)).success).toBe(false);
    }
  });

  it('omits blank optional fields but retains postal leading zeros', () => {
    const input = validInput();
    Object.assign(input.pickup, { addressLine2: '', postalCode: '00100' });
    Object.assign(input.dropoff, { contactEmail: '', postalCode: ' ' });
    input.parcel.description = '';
    input.deliveryInstructions = '';
    const output = createDeliverySchema(references()).parse(input);
    expect(output.pickup.postalCode).toBe('00100');
    expect(output.pickup.addressLine2).toBeUndefined();
    expect(output.dropoff.contactEmail).toBeUndefined();
    expect(output.dropoff.postalCode).toBeUndefined();
  });
  it.each([
    ['dropoff.contactEmail', 'recipient@example.com', true], ['dropoff.contactEmail', 'bad@', false],
    ['dropoff.contactEmail', `${'a'.repeat(250)}@example.com`, false],
    ['parcel.description', 'x'.repeat(200), true], ['parcel.description', 'x'.repeat(201), false],
    ['deliveryInstructions', 'x'.repeat(300), true], ['deliveryInstructions', 'x'.repeat(301), false],
    ['parcel.size', 'LARGE', false], ['parcel.size', '', false], ['parcel.size', undefined, false],
  ])('validates %s boundaries', (path, value, success) => {
    expect(createDeliverySchema(references()).safeParse(setField(validInput(), path, value)).success).toBe(success);
  });
  it('obtains size and district membership exclusively from reference data', () => {
    const data = references();
    data.sizes = [{ size: 'LARGE' }];
    data.districts = [{ code: 'KANDY' }];
    const input = validInput();
    expect(createDeliverySchema(data).safeParse(input).success).toBe(false);
    input.pickup.districtCode = 'KANDY';
    input.parcel.size = 'LARGE';
    expect(createDeliverySchema(data).safeParse(input).success).toBe(true);
  });
  it('requires pickup, dropoff and parcel objects', () => {
    for (const field of ['pickup', 'dropoff', 'parcel']) {
      const input = validInput();
      delete input[field];
      expect(createDeliverySchema(references()).safeParse(input).success).toBe(false);
    }
  });
});

function ValidationHarness({ onValid }) {
  const { register, handleSubmit, formState: { errors } } = useForm({
    defaultValues: validInput(), resolver: createDeliveryResolver(references('2.50')),
  });
  return (
    <form onSubmit={handleSubmit(onValid)}>
      <label>Weight<input {...register('parcel.weightKg')} /></label>
      {errors.parcel?.weightKg && <span role="alert">{errors.parcel.weightKg.message}</span>}
      <button type="submit">Validate</button>
    </form>
  );
}

describe('TST-040 React Hook Form Zod integration', () => {
  it('shows nested field errors, preserves invalid input, and submits corrected decimal text', async () => {
    const onValid = vi.fn();
    render(<ValidationHarness onValid={onValid} />);
    try {
      fireEvent.change(screen.getByLabelText('Weight'), { target: { value: '2.51' } });
      fireEvent.click(screen.getByRole('button', { name: 'Validate' }));
      expect(await screen.findByRole('alert')).toHaveTextContent('2.50');
      expect(screen.getByLabelText('Weight')).toHaveValue('2.51');
      expect(onValid).not.toHaveBeenCalled();
      fireEvent.change(screen.getByLabelText('Weight'), { target: { value: '2.50' } });
      fireEvent.click(screen.getByRole('button', { name: 'Validate' }));
      await waitFor(() => expect(onValid).toHaveBeenCalledTimes(1));
      expect(onValid.mock.calls[0][0].parcel.weightKg).toBe('2.50');
      expect(onValid.mock.calls[0][0].pickup.contactPhone).toBe('+94771234567');
    } finally {
      cleanup();
    }
  });
});
