import mongoose from 'mongoose';
const { Schema } = mongoose;
export const Drive = mongoose.model(
  'Drive',
  new Schema(
    {
      filename: { type: String, required: true },
      sizeBytes: Number,
      phoneModel: String,
      mount: String,
      route: String,
      notes: String,
      sessionId: { type: String, index: true, unique: true, sparse: true },
      durationSeconds: Number,
      distanceMeters: Number,
      source: { type: String, enum: ['device', 'simulation'], default: 'device' },
      startedAt: Date,
      uploadedAt: { type: Date, default: Date.now },
    },
    { versionKey: false },
  ),
);
export const Model = mongoose.model(
  'Model',
  new Schema(
    {
      name: String,
      version: String,
      filename: String,
      sizeBytes: Number,
      sha256: String,
      placeholder: { type: Boolean, default: true },
      validated: { type: Boolean, default: false },
      dataset: String,
      uploadedAt: { type: Date, default: Date.now },
    },
    { versionKey: false },
  ),
);
Model.schema.index({ name: 1, version: 1 }, { unique: true });
export const Report = mongoose.model(
  'Report',
  new Schema(
    {
      driveId: { type: Schema.Types.ObjectId, ref: 'Drive', required: true, unique: true },
      ateMeters: Number,
      driftPercent: Number,
      timestamps: [Number],
      driftMeters: [Number],
      createdAt: { type: Date, default: Date.now },
    },
    { versionKey: false },
  ),
);
