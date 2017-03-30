package accel
import templates._
import types._
import chisel3._
trait x4444_unrForeach extends x4469_unrForeach {
  // Controller Stack: Stack(x4469, x4470)
  b3117 := x4351_ctr(0)
  val x3119 = b3118 && b2961
  // Assemble multidimR vector
  val x4353_parLd_rVec = Wire(Vec(1, new multidimR(1, 32)))
  x4353_parLd_rVec(0).en := x4444_unrForeach_en
  x4353_parLd_rVec(0).addr(0) := b3117 
  x4218_priceBlk_0.connectRPort(Vec(x4353_parLd_rVec.toArray), 1)
  val x4353_parLd = (0 until 1).map{i => Utils.FixedPoint(true,16,16, x4218_priceBlk_0.io.output.data(1*1+i))}
  // Assemble multidimR vector
  val x4354_parLd_rVec = Wire(Vec(1, new multidimR(1, 32)))
  x4354_parLd_rVec(0).en := x4444_unrForeach_en
  x4354_parLd_rVec(0).addr(0) := b3117 
  x4219_strikeBlk_0.connectRPort(Vec(x4354_parLd_rVec.toArray), 1)
  val x4354_parLd = (0 until 1).map{i => Utils.FixedPoint(true,16,16, x4219_strikeBlk_0.io.output.data(1*1+i))}
  // Assemble multidimR vector
  val x4355_parLd_rVec = Wire(Vec(1, new multidimR(1, 32)))
  x4355_parLd_rVec(0).en := x4444_unrForeach_en
  x4355_parLd_rVec(0).addr(0) := b3117 
  x4220_rateBlk_0.connectRPort(Vec(x4355_parLd_rVec.toArray), 1)
  val x4355_parLd = (0 until 1).map{i => Utils.FixedPoint(true,16,16, x4220_rateBlk_0.io.output.data(1*1+i))}
  // Assemble multidimR vector
  val x4356_parLd_rVec = Wire(Vec(1, new multidimR(1, 32)))
  x4356_parLd_rVec(0).en := x4444_unrForeach_en
  x4356_parLd_rVec(0).addr(0) := b3117 
  x4221_volBlk_0.connectRPort(Vec(x4356_parLd_rVec.toArray), 1)
  val x4356_parLd = (0 until 1).map{i => Utils.FixedPoint(true,16,16, x4221_volBlk_0.io.output.data(1*1+i))}
  // Assemble multidimR vector
  val x4357_parLd_rVec = Wire(Vec(1, new multidimR(1, 32)))
  x4357_parLd_rVec(0).en := x4444_unrForeach_en
  x4357_parLd_rVec(0).addr(0) := b3117 
  x4222_timeBlk_0.connectRPort(Vec(x4357_parLd_rVec.toArray), 1)
  val x4357_parLd = (0 until 1).map{i => Utils.FixedPoint(true,16,16, x4222_timeBlk_0.io.output.data(1*1+i))}
  // Assemble multidimR vector
  val x4358_parLd_rVec = Wire(Vec(1, new multidimR(1, 32)))
  x4358_parLd_rVec(0).en := x4444_unrForeach_en
  x4358_parLd_rVec(0).addr(0) := b3117 
  x4217_typeBlk_0.connectRPort(Vec(x4358_parLd_rVec.toArray), 1)
  val x4358_parLd = (0 until 1).map{i => Utils.FixedPoint(true,32,0, x4217_typeBlk_0.io.output.data(1*1+i))}
  val x4359_elem0 = x4353_parLd.apply(0)
  val x4360_elem0 = x4354_parLd.apply(0)
  val x4361 = x4359_elem0 / x4360_elem0
  val x4362_elem0 = x4357_parLd.apply(0)
  val x4363_elem0 = x4355_parLd.apply(0)
  val x4364_elem0 = x4356_parLd.apply(0)
  val x4365 = x4364_elem0 * x4364_elem0
  val x4366 = x4365 * Utils.FixedPoint(true,16,16,0.5)
  val x4367_sumx4363_x4366 = x4363_elem0 + x4366
  val x4368 = x4367_sumx4363_x4366 * x4362_elem0
  val x4369_sumx4368_x4361 = x4368 + x4361
  val x4370 = x4364_elem0 * x4362_elem0
  val x4371 = x4370 * x4362_elem0
  val x4372 = x4371 * x4371
  val x4373 = x4369_sumx4368_x4361 / x4372
  val x4374 = Mux(x4373 < 0.U, -x4373, x4373)
  val x4375 = x4374 * x4374
  val x4376 = x4375 * Utils.FixedPoint(true,16,16,-0.05)
  val x4377 = x4376 * Utils.FixedPoint(true,16,16,0.3989422804014327)
  val x4378 = x4374 * Utils.FixedPoint(true,16,16,0.2316419)
  val x4379_sumx4378_unk = x4378 + Utils.FixedPoint(true,16,16,1.0)
  val x4380 = Utils.FixedPoint(true,16,16,1) / x4379_sumx4378_unk
  val x4381 = x4380 * Utils.FixedPoint(true,16,16,0.31938153)
  val x4382 = x4380 * x4380
  val x4383 = x4382 * x4380
  val x4384 = x4383 * x4380
  val x4385 = x4384 * x4380
  val x4386 = x4385 * Utils.FixedPoint(true,16,16,1.330274429)
  val x4387 = x4384 * Utils.FixedPoint(true,16,16,-1.821255978)
  val x4388 = x4382 * Utils.FixedPoint(true,16,16,-0.356563782)
  val x4389 = x4383 * Utils.FixedPoint(true,16,16,1.781477937)
  val x4390_sumx4388_x4389 = x4388 + x4389
  val x4391_sumx4390_x4387 = x4390_sumx4388_x4389 + x4387
  val x4392_sumx4391_x4386 = x4391_sumx4390_x4387 + x4386
  val x4393_sumx4392_x4381 = x4392_sumx4391_x4386 + x4381
  val x4394 = x4393_sumx4392_x4381 * x4377
  val x4395_negx4394 = -x4394
  val x4396_sumx4395_unk = x4395_negx4394 + Utils.FixedPoint(true,16,16,1.0)
  val x4397 = x4373 < Utils.FixedPoint(true,16,16,0.0)
  val x4398 = Mux((x4397), x4394, x4396_sumx4395_unk)
  val x4399 = x4359_elem0 * x4398
  val x4400 = x4373 - x4371
  val x4401 = Mux(x4400 < 0.U, -x4400, x4400)
  val x4402 = x4401 * x4401
  val x4403 = x4402 * Utils.FixedPoint(true,16,16,-0.05)
  val x4404 = x4403 * Utils.FixedPoint(true,16,16,0.3989422804014327)
  val x4405 = x4401 * Utils.FixedPoint(true,16,16,0.2316419)
  val x4406_sumx4405_unk = x4405 + Utils.FixedPoint(true,16,16,1.0)
  val x4407 = Utils.FixedPoint(true,16,16,1) / x4406_sumx4405_unk
  val x4408 = x4407 * Utils.FixedPoint(true,16,16,0.31938153)
  val x4409 = x4407 * x4407
  val x4410 = x4409 * x4407
  val x4411 = x4410 * x4407
  val x4412 = x4411 * x4407
  val x4413 = x4412 * Utils.FixedPoint(true,16,16,1.330274429)
  val x4414 = x4411 * Utils.FixedPoint(true,16,16,-1.821255978)
  val x4415 = x4409 * Utils.FixedPoint(true,16,16,-0.356563782)
  val x4416 = x4410 * Utils.FixedPoint(true,16,16,1.781477937)
  val x4417_sumx4415_x4416 = x4415 + x4416
  val x4418_sumx4417_x4414 = x4417_sumx4415_x4416 + x4414
  val x4419_sumx4418_x4413 = x4418_sumx4417_x4414 + x4413
  val x4420_sumx4419_x4408 = x4419_sumx4418_x4413 + x4408
  val x4421 = x4420_sumx4419_x4408 * x4404
  val x4422_negx4421 = -x4421
  val x4423_sumx4422_unk = x4422_negx4421 + Utils.FixedPoint(true,16,16,1.0)
  val x4424 = x4400 < Utils.FixedPoint(true,16,16,0.0)
  val x4425 = Mux((x4424), x4421, x4423_sumx4422_unk)
  val x4426_negx4363 = -x4363_elem0
  val x4427 = x4360_elem0 * x4426_negx4363
  val x4428 = x4362_elem0 // should be fixpt argon.ops.FixPtExp$FixPtType@f4b7853f
  val x4429 = x4427 * x4428
  val x4430 = x4429 * x4425
  val x4431 = x4399 - x4430
  val x4432_negx4425 = -x4425
  val x4433_sumx4432_unk = x4432_negx4425 + Utils.FixedPoint(true,16,16,1.0)
  val x4434 = x4429 * x4433_sumx4432_unk
  val x4435_negx4398 = -x4398
  val x4436_sumx4435_unk = x4435_negx4398 + Utils.FixedPoint(true,16,16,1.0)
  val x4437 = x4359_elem0 * x4436_sumx4435_unk
  val x4438 = x4434 - x4437
  val x4439_elem0 = x4358_parLd.apply(0)
  val x4440 = x4439_elem0 === 0.U(32.W)
  val x4441 = Mux((x4440), x4438, x4431)
  val x4442_vecified = Array(x4441)
  // Assemble multidimW vector
  val x4443_parSt_wVec = Wire(Vec(1, new multidimW(1, 32))) 
  x4443_parSt_wVec.zip(x4442_vecified).foreach{ case (port, dat) => port.data := dat.number }
  x4443_parSt_wVec(0).en := x3119
  x4443_parSt_wVec(0).addr(0) := b3117 
  x4223_optpriceBlk_0.connectWPort(x4443_parSt_wVec, x4444_unrForeach_datapath_en, List(0))
  // results in ()
}
